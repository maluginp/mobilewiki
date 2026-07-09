package app.obsidianmd.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.conflict_keep_local
import app.obsidianmd.resources.conflict_message
import app.obsidianmd.resources.conflict_take_server
import app.obsidianmd.resources.conflict_title
import app.obsidianmd.sync.MdConflict
import app.obsidianmd.sync.Resolution
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConflictDialog(conflict: MdConflict, onChoose: (Resolution) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.conflict_title, conflict.path)) },
        text = { Text(stringResource(Res.string.conflict_message)) },
        confirmButton = {
            TextButton(onClick = { onChoose(Resolution.USE_LOCAL) }) {
                Text(stringResource(Res.string.conflict_keep_local))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(Resolution.USE_SERVER) }) {
                Text(stringResource(Res.string.conflict_take_server))
            }
        },
    )
}
