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
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_apply
import app.obsidianmd.resources.action_reject
import app.obsidianmd.resources.action_send
import app.obsidianmd.resources.ai_thinking
import app.obsidianmd.resources.ai_write_title
import app.obsidianmd.resources.chat_ai_prefix
import app.obsidianmd.resources.chat_you_prefix
import app.obsidianmd.resources.error_with_reason
import org.jetbrains.compose.resources.stringResource

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
                val prefix = stringResource(
                    if (turn.role == "user") Res.string.chat_you_prefix else Res.string.chat_ai_prefix,
                )
                Text("$prefix ${turn.text}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        if (status is AiStatus.Thinking) {
            Text(stringResource(Res.string.ai_thinking), Modifier.padding(horizontal = 16.dp))
        }
        if (status is AiStatus.Failed) {
            Text(
                stringResource(Res.string.error_with_reason, status.reason),
                Modifier.padding(horizontal = 16.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f))
            Button(onClick = { if (input.isNotBlank()) { onSend(input); input = "" } }) {
                Text(stringResource(Res.string.action_send))
            }
        }
    }
    if (pendingWrite != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.ai_write_title, pendingWrite.first)) },
            text = { Text(pendingWrite.second.take(500)) },
            confirmButton = { TextButton(onClick = onApprove) { Text(stringResource(Res.string.action_apply)) } },
            dismissButton = { TextButton(onClick = onReject) { Text(stringResource(Res.string.action_reject)) } },
        )
    }
}
