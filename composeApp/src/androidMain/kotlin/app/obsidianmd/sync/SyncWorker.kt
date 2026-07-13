package app.obsidianmd.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.obsidianmd.auth.EncryptedTokenStore
import app.obsidianmd.settings.SharedPrefsRepoSettingsStore
import app.obsidianmd.vault.data.vaultRootPath

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val tokenStore = EncryptedTokenStore(ctx)
        val settingsStore = SharedPrefsRepoSettingsStore(ctx)
        val vaultPath = vaultRootPath(ctx)
        val runner = BackgroundSyncRunner(JGitSync()) {
            settingsStore.getRemoteUrl()?.takeIf { it.isNotBlank() }?.let { url ->
                SyncConfig(remoteUrl = url, localPath = vaultPath, token = tokenStore.get())
            }
        }
        return when (runner.run()) {
            is SyncResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }
}
