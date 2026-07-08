package app.obsidianmd.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AutoSyncScheduler(private val context: Context) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "obsidian-autosync",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork("obsidian-autosync")
    }
}
