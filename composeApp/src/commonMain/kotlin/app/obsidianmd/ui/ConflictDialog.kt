package app.obsidianmd.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.obsidianmd.sync.MdConflict
import app.obsidianmd.sync.Resolution

@Composable
fun ConflictDialog(conflict: MdConflict, onChoose: (Resolution) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Conflict: ${conflict.path}") },
        text = { Text("This file was changed both locally and on the server. Which version do you want to keep?") },
        confirmButton = {
            TextButton(onClick = { onChoose(Resolution.USE_LOCAL) }) { Text("Keep local") }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(Resolution.USE_SERVER) }) { Text("Take server") }
        },
    )
}
