package app.obsidianmd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        val store = EncryptedTokenStore(applicationContext)
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

            MaterialTheme {
                Surface {
                    if (!loggedIn) {
                        LoginScreen(
                            state = authState,
                            onLogin = authVm::login,
                            onOpenUrl = { url ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                        )
                    } else {
                        val syncConfig = BuildConfig.SYNC_REMOTE_URL.takeIf { it.isNotBlank() }?.let {
                            SyncConfig(remoteUrl = it, localPath = root.toString(), token = store.get())
                        }
                        val vm = VaultViewModel(
                            repo, lifecycleScope, Dispatchers.IO,
                            gitSync = JGitSync(), syncConfig = syncConfig,
                            resolver = UiConflictResolver(),
                        )
                        App(vm)
                    }
                }
            }
        }
    }
}
