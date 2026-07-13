package app.obsidianmd

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.auth.TokenStore
import app.obsidianmd.nav.AppNavHost
import app.obsidianmd.nav.Route
import app.obsidianmd.nav.startStack
import app.obsidianmd.settings.RepoSettingsStore
import app.obsidianmd.sync.AutoSyncScheduler
import app.obsidianmd.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.KoinContext
import org.koin.compose.getKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TopAppBar draws under the status bar → no grey strip. auto() picks icon color from the
        // system dark-mode setting — light theme → dark icons, dark theme → light icons — matching
        // AppTheme (which follows isSystemInDarkTheme()). No configChanges declared → the activity
        // recreates on theme switch and this re-runs with the new mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        setContent {
            KoinContext {
                // Koin singletons are lazy; the EncryptedSharedPreferences-backed stores are
                // heavy (decryption). Force-construct them off the main thread before first frame,
                // otherwise composition builds them on the main thread and we catch an ANR.
                // Здесь же считаем стартовый бэкстек (токен + репозиторий) и планируем автосинк.
                var startRoutes by remember { mutableStateOf<List<Route>?>(null) }
                val koin = getKoin()
                LaunchedEffect(Unit) {
                    startRoutes = withContext(Dispatchers.IO) {
                        koin.get<ApiKeyStore>()
                        val hasToken = koin.get<TokenStore>().get() != null
                        val hasRepo = !koin.get<RepoSettingsStore>().getRemoteUrl().isNullOrBlank()
                        if (hasToken && hasRepo) AutoSyncScheduler(applicationContext).schedule()
                        startStack(hasToken = hasToken, hasRepo = hasRepo)
                    }
                }
                AppTheme {
                    Surface {
                        val start = startRoutes
                        if (start == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            AppNavHost(start)
                        }
                    }
                }
            }
        }
    }
}
