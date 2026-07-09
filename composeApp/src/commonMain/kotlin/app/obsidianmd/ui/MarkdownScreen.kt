package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MarkdownScreen(content: String, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(content) { mutableStateOf(content) }
    Column(Modifier.fillMaxSize()) {
        if (!editing) {
            Row {
                TextButton(onClick = { draft = content; editing = true }) { Text("Редактировать") }
            }
        }
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                Button(onClick = { onSave(draft); editing = false }) { Text("Сохранить") }
                TextButton(onClick = { editing = false }) { Text("Отмена") }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Markdown(content)
            }
        }
    }
}
