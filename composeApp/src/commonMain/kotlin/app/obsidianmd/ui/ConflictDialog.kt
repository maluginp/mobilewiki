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
        title = { Text("Конфликт: ${conflict.path}") },
        text = { Text("Файл изменён и локально, и на сервере. Какую версию оставить?") },
        confirmButton = {
            TextButton(onClick = { onChoose(Resolution.USE_LOCAL) }) { Text("Оставить локальную") }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(Resolution.USE_SERVER) }) { Text("Взять серверную") }
        },
    )
}
