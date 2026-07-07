package app.obsidianmd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.obsidianmd.sync.JGitSync
import app.obsidianmd.sync.SyncConfig
import app.obsidianmd.sync.UiConflictResolver
import app.obsidianmd.ui.VaultViewModel
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = createRepository(applicationContext)
        val root = vaultRoot(applicationContext)
        val syncConfig = BuildConfig.SYNC_REMOTE_URL.takeIf { it.isNotBlank() }?.let { url ->
            SyncConfig(
                remoteUrl = url,
                localPath = root.toString(),
                token = BuildConfig.SYNC_TOKEN.takeIf { it.isNotBlank() },
            )
        }
        val vm = VaultViewModel(
            repo, lifecycleScope, Dispatchers.IO,
            gitSync = JGitSync(),
            syncConfig = syncConfig,
            resolver = UiConflictResolver(),
        )
        setContent { App(vm) }
    }
}
