package app.obsidianmd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.auth.AuthState
import app.obsidianmd.auth.AuthViewModel
import app.obsidianmd.auth.RepoPickerViewModel
import app.obsidianmd.auth.RepoValidationViewModel
import app.obsidianmd.auth.TokenStore
import app.obsidianmd.settings.RepoSettingsStore
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.ui.LoginScreen
import app.obsidianmd.ui.ManualUrlScreen
import app.obsidianmd.ui.RepoPickerScreen
import app.obsidianmd.ui.RepoValidationScreen
import app.obsidianmd.ui.VaultViewModel
import app.obsidianmd.ui.WelcomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.KoinContext
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TopAppBar draws under the status bar → no grey strip. Force LIGHT bars (dark icons):
        // the UI is always light (MaterialTheme = lightColorScheme), so default auto() would pick
        // white icons in system dark mode → invisible on our light background.
        // ponytail: hard-coded light bars because the app has no dark theme; revisit if one is added.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            KoinContext {
                // Koin singletons are lazy; the EncryptedSharedPreferences-backed stores are
                // heavy (decryption). Force-construct them off the main thread before first frame,
                // otherwise composition builds them on the main thread and we catch an ANR.
                var initialLoggedIn by remember { mutableStateOf<Boolean?>(null) }
                val koin = getKoin()
                LaunchedEffect(Unit) {
                    initialLoggedIn = withContext(Dispatchers.IO) {
                        koin.get<ApiKeyStore>()
                        koin.get<RepoSettingsStore>()
                        koin.get<TokenStore>().get() != null
                    }
                }
                MaterialTheme {
                    Surface {
                        val loggedIn = initialLoggedIn
                        if (loggedIn == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Gate(loggedIn)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Gate(initialLoggedIn: Boolean) {
        val settingsVm: SettingsViewModel = koinViewModel()
        val authVm: AuthViewModel = koinViewModel()
        var loggedIn by remember { mutableStateOf(initialLoggedIn) }
        val authState by authVm.state.collectAsState()
        if (authState is AuthState.Success && !loggedIn) loggedIn = true
        val settings by settingsVm.state.collectAsState()
        val hasRepo = settings.url.isNotBlank()
        var changingRepo by remember { mutableStateOf(false) }
        val vaultVm: VaultViewModel = koinViewModel()

        if (loggedIn && hasRepo) {
            LaunchedEffect(Unit) {
                app.obsidianmd.sync.AutoSyncScheduler(applicationContext).schedule()
            }
        }

        when {
            // 1. Нет токена — приветствие, затем device-авторизация.
            !loggedIn -> Box(Modifier.safeDrawingPadding()) {
                if (authState is AuthState.Idle) {
                    WelcomeScreen(onSignIn = authVm::login)
                } else {
                    LoginScreen(
                        state = authState,
                        onLogin = authVm::login,
                        onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    )
                }
            }
            // 2. Токен есть, но репозиторий не выбран (или пользователь меняет его из настроек).
            !hasRepo || changingRepo -> {
                var step by remember { mutableStateOf(RepoStep.List) }
                var candidate by remember { mutableStateOf("") }
                val pickerVm: RepoPickerViewModel = koinViewModel()
                LaunchedEffect(Unit) { pickerVm.load() }
                val picked by pickerVm.picked.collectAsState()
                LaunchedEffect(picked) { picked?.let { candidate = it; step = RepoStep.Validate } }
                val validationVm: RepoValidationViewModel = koinViewModel()
                // «Назад» есть только когда есть куда вернуться (смена репо из настроек);
                // при первом выборе репозиторий обязателен, поэтому выхода нет.
                val exit: (() -> Unit)? = if (hasRepo) ({ changingRepo = false }) else null
                Box(Modifier.safeDrawingPadding()) {
                    when (step) {
                        RepoStep.List -> {
                            val pickerState by pickerVm.state.collectAsState()
                            RepoPickerScreen(
                                state = pickerState,
                                onChoose = pickerVm::pick,
                                onRetry = pickerVm::load,
                                onEnterManually = { step = RepoStep.Manual },
                                onBack = exit,
                            )
                        }
                        RepoStep.Manual -> ManualUrlScreen(
                            onSubmit = { url -> candidate = url; step = RepoStep.Validate },
                            onBack = { step = RepoStep.List },
                        )
                        RepoStep.Validate -> {
                            LaunchedEffect(candidate) { validationVm.validate(candidate) }
                            val vs by validationVm.state.collectAsState()
                            RepoValidationScreen(
                                state = vs,
                                // Сменили репо → сохраняем URL и сразу синкаем: JGitSync снесёт старый
                                // клон (origin не совпадает) и выкачает новый, чтобы не остались старые файлы.
                                onContinue = { settingsVm.save(candidate); changingRepo = false; vaultVm.sync() },
                                onRetry = { validationVm.validate(candidate) },
                                onBack = { step = RepoStep.List },
                            )
                        }
                    }
                }
            }
            // 3. Токен + репозиторий — основной экран.
            else -> {
                val aiEnabled = settings.aiEnabled
                val aiModel = settings.aiModel
                val apiKeyStore: ApiKeyStore = koinInject()
                // Чтение ключа зашифровано — не дёргаем его на каждой рекомпозиции, только при смене флага/модели.
                val aiKey = remember(aiEnabled, aiModel) {
                    apiKeyStore.getKey()?.takeIf { it.isNotBlank() && aiEnabled }
                }
                // key = aiModel: смена модели создаёт новый ViewModel с новым клиентом (история чата сбрасывается).
                val aiVm: AiViewModel? = aiKey?.let { key ->
                    koinViewModel(key = aiModel) { parametersOf(aiModel, key) }
                }
                App(
                    vaultVm,
                    settingsVm,
                    aiVm,
                    onPickRepoFromGitHub = { changingRepo = true },
                )
            }
        }
    }
}

private enum class RepoStep { List, Manual, Validate }
