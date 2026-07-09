package app.obsidianmd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.obsidianmd.auth.AuthState
import app.obsidianmd.auth.AuthViewModel
import app.obsidianmd.auth.EncryptedTokenStore
import app.obsidianmd.auth.GitHubDeviceAuth
import app.obsidianmd.sync.JGitSync
import app.obsidianmd.sync.SyncConfig
import app.obsidianmd.sync.UiConflictResolver
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.settings.SharedPrefsRepoSettingsStore
import app.obsidianmd.ui.LoginScreen
import app.obsidianmd.ui.VaultViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // TopAppBar draws under the status bar → no grey strip
        val store = EncryptedTokenStore(applicationContext)
        val settingsStore = SharedPrefsRepoSettingsStore(applicationContext)
        val apiKeyStore = app.obsidianmd.ai.EncryptedApiKeyStore(applicationContext)
        val settingsVm = SettingsViewModel(settingsStore, apiKeyStore)
        val repo = createRepository(applicationContext)
        val root = vaultRoot(applicationContext)

        val http = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val deviceAuth = GitHubDeviceAuth(http, BuildConfig.GITHUB_CLIENT_ID)
        val authVm = AuthViewModel(deviceAuth, store, lifecycleScope)

        setContent {
            var loggedIn by remember { mutableStateOf(store.get() != null) }
            val authState by authVm.state.collectAsState()
            if (authState is AuthState.Success && !loggedIn) loggedIn = true

            if (loggedIn) {
                LaunchedEffect(Unit) {
                    app.obsidianmd.sync.AutoSyncScheduler(applicationContext).schedule()
                }
            }

            MaterialTheme {
                Surface {
                    if (!loggedIn) {
                        // no Scaffold here, so keep content clear of the system bars
                        androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                            LoginScreen(
                                state = authState,
                                onLogin = authVm::login,
                                onOpenUrl = { url ->
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                            )
                        }
                    } else {
                        val vm = VaultViewModel(
                            repo, lifecycleScope, Dispatchers.IO,
                            gitSync = JGitSync(),
                            syncConfigProvider = {
                                settingsStore.getRemoteUrl()?.takeIf { it.isNotBlank() }?.let { url ->
                                    SyncConfig(remoteUrl = url, localPath = root.toString(), token = store.get())
                                }
                            },
                            resolver = UiConflictResolver(),
                        )
                        val aiVm = apiKeyStore.getKey()
                            ?.takeIf { it.isNotBlank() && settingsStore.isAiEnabled() }
                            ?.let { key ->
                            val client = app.obsidianmd.ai.OpenRouterClient(http, key)
                            app.obsidianmd.ai.AiViewModel(
                                runAgent = { history, approver ->
                                    app.obsidianmd.ai.AiAgent(client, repo, approver).ask(history)
                                },
                                scope = lifecycleScope,
                            )
                        }
                        App(vm, settingsVm, aiVm)
                    }
                }
            }
        }
    }
}
