package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown

// Правкой управляет AppBar (иконки Edit/Save); экран лишь показывает просмотр или редактор.
@Composable
fun MarkdownScreen(
    content: String,
    editing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            )
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Markdown(content)
            }
        }
    }
}
