package app.obsidianmd

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.viewmodel.compose.viewModel
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.ai.EncryptedApiKeyStore
import app.obsidianmd.auth.AuthState
import app.obsidianmd.auth.AuthViewModel
import app.obsidianmd.auth.EncryptedTokenStore
import app.obsidianmd.auth.GitHubDeviceAuth
import app.obsidianmd.auth.GitHubRepoAccess
import app.obsidianmd.auth.GitHubRepos
import app.obsidianmd.auth.RepoPickerViewModel
import app.obsidianmd.auth.RepoValidationViewModel
import app.obsidianmd.auth.TokenStore
import app.obsidianmd.sync.JGitSync
import app.obsidianmd.sync.SyncConfig
import app.obsidianmd.sync.UiConflictResolver
import app.obsidianmd.settings.RepoSettingsStore
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.settings.SharedPrefsRepoSettingsStore
import app.obsidianmd.ui.LoginScreen
import app.obsidianmd.ui.ManualUrlScreen
import app.obsidianmd.ui.RepoPickerScreen
import app.obsidianmd.ui.RepoValidationScreen
import app.obsidianmd.ui.VaultViewModel
import app.obsidianmd.ui.WelcomeScreen
import app.obsidianmd.vault.VaultRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Path

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // TopAppBar draws under the status bar → no grey strip

        setContent {
            // Тяжёлая инициализация (два EncryptedSharedPreferences + расшифровка токена)
            // уводится с главного потока, иначе первый кадр ждёт её и ловит ANR.
            var deps by remember { mutableStateOf<Deps?>(null) }
            LaunchedEffect(Unit) {
                deps = withContext(Dispatchers.IO) { buildDeps(applicationContext) }
            }
            MaterialTheme {
                Surface {
                    val d = deps
                    if (d == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Gate(d)
                    }
                }
            }
        }
    }

    @Composable
    private fun Gate(d: Deps) {
        val settingsVm: SettingsViewModel = viewModel {
            SettingsViewModel(
                d.settingsStore, d.apiKeyStore,
                fetchModels = { app.obsidianmd.ai.fetchModels(d.http, d.apiKeyStore.getKey().orEmpty()) },
            )
        }
        val authVm: AuthViewModel = viewModel {
            AuthViewModel(GitHubDeviceAuth(d.http, BuildConfig.GITHUB_CLIENT_ID), d.store)
        }
        var loggedIn by remember { mutableStateOf(d.initialLoggedIn) }
        val authState by authVm.state.collectAsState()
        if (authState is AuthState.Success && !loggedIn) loggedIn = true
        val settings by settingsVm.state.collectAsState()
        val hasRepo = settings.url.isNotBlank()
        var changingRepo by remember { mutableStateOf(false) }
        val vaultVm: VaultViewModel = viewModel {
            VaultViewModel(
                d.repo, Dispatchers.IO,
                gitSync = JGitSync(),
                syncConfigProvider = {
                    d.settingsStore.getRemoteUrl()?.takeIf { it.isNotBlank() }?.let { url ->
                        SyncConfig(remoteUrl = url, localPath = d.root.toString(), token = d.store.get())
                    }
                },
                resolver = UiConflictResolver(),
            )
        }

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
                val pickerVm: RepoPickerViewModel = viewModel {
                    RepoPickerViewModel(repos = GitHubRepos(d.http), token = d.store::get)
                }
                LaunchedEffect(Unit) { pickerVm.load() }
                val picked by pickerVm.picked.collectAsState()
                LaunchedEffect(picked) { picked?.let { candidate = it; step = RepoStep.Validate } }
                val validationVm: RepoValidationViewModel = viewModel {
                    RepoValidationViewModel(GitHubRepoAccess(d.http), d.store::get)
                }
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
                // Чтение ключа зашифровано — не дёргаем его на каждой рекомпозиции, только при смене флага/модели.
                val aiKey = remember(aiEnabled, aiModel) {
                    d.apiKeyStore.getKey()?.takeIf { it.isNotBlank() && aiEnabled }
                }
                // key = aiModel: смена модели создаёт новый ViewModel с новым клиентом (история чата сбрасывается).
                val aiVm: AiViewModel? = aiKey?.let { key ->
                    viewModel(key = aiModel) {
                        val client = app.obsidianmd.ai.OpenRouterClient(d.http, key, aiModel)
                        AiViewModel { history, approver ->
                            app.obsidianmd.ai.AiAgent(client, d.repo, approver).ask(history)
                        }
                    }
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

/** Все зависимости приложения, собранные вне главного потока. ViewModel'и создаются в композиции через viewModel {}. */
private class Deps(
    val store: TokenStore,
    val settingsStore: RepoSettingsStore,
    val apiKeyStore: ApiKeyStore,
    val repo: VaultRepository,
    val root: Path,
    val http: HttpClient,
    val initialLoggedIn: Boolean,
)

private fun buildDeps(context: Context): Deps {
    val store = EncryptedTokenStore(context)
    val settingsStore = SharedPrefsRepoSettingsStore(context)
    val apiKeyStore = EncryptedApiKeyStore(context)
    val repo = createRepository(context)
    val root = vaultRoot(context)
    val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    return Deps(store, settingsStore, apiKeyStore, repo, root, http, store.get() != null)
}
