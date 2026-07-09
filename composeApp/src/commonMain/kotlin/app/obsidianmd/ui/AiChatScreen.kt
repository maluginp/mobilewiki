package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.ai.AiStatus
import app.obsidianmd.ai.ChatTurn

@Composable
fun AiChatScreen(
    messages: List<ChatTurn>,
    status: AiStatus,
    pendingWrite: Pair<String, String>?,
    onSend: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(messages) { turn ->
                Text(
                    (if (turn.role == "user") "Вы: " else "AI: ") + turn.text,
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (status is AiStatus.Thinking) Text("AI думает…", Modifier.padding(horizontal = 16.dp))
        if (status is AiStatus.Failed) Text("Ошибка: ${status.reason}", Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f))
            Button(onClick = { if (input.isNotBlank()) { onSend(input); input = "" } }) { Text("Отправить") }
        }
    }
    if (pendingWrite != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI предлагает запись: ${pendingWrite.first}") },
            text = { Text(pendingWrite.second.take(500)) },
            confirmButton = { TextButton(onClick = onApprove) { Text("Применить") } },
            dismissButton = { TextButton(onClick = onReject) { Text("Отклонить") } },
        )
    }
}
