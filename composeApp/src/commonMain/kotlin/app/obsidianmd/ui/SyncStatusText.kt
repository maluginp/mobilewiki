package app.obsidianmd.ui

import androidx.compose.runtime.Composable
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.error_with_reason
import app.obsidianmd.resources.sync_done_cloned
import app.obsidianmd.resources.sync_synced
import app.obsidianmd.resources.sync_synced_conflicts
import app.obsidianmd.resources.sync_syncing
import app.obsidianmd.resources.sync_up_to_date
import app.obsidianmd.sync.SyncResult
import org.jetbrains.compose.resources.stringResource

/** Текст статуса синка для UI. Живёт в shell (composeApp): фичи про SyncStatus не знают. */
@Composable
internal fun syncStatusText(status: SyncStatus): String = when (status) {
    SyncStatus.Idle -> ""
    SyncStatus.Running -> stringResource(Res.string.sync_syncing)
    is SyncStatus.Done -> when (val r = status.result) {
        is SyncResult.Cloned -> stringResource(Res.string.sync_done_cloned)
        is SyncResult.UpToDate -> stringResource(Res.string.sync_up_to_date)
        is SyncResult.Synced ->
            if (r.conflictsResolved > 0) stringResource(Res.string.sync_synced_conflicts, r.conflictsResolved)
            else stringResource(Res.string.sync_synced)
        is SyncResult.Failed -> stringResource(Res.string.error_with_reason, r.reason)
    }
}
