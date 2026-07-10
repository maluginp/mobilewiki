package app.obsidianmd.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.obsidianmd.editor.EditState
import app.obsidianmd.editor.MdEdit

/** Панель форматирования: каждая кнопка отдаёт наверх трансформацию над текущим EditState. */
@Composable
fun EditorToolbar(
    onTransform: ((EditState) -> EditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        IconButton(onClick = { onTransform { MdEdit.wrapInline(it, "**") } }) {
            Icon(Icons.Filled.FormatBold, contentDescription = "Bold")
        }
        IconButton(onClick = { onTransform { MdEdit.wrapInline(it, "*") } }) {
            Icon(Icons.Filled.FormatItalic, contentDescription = "Italic")
        }
        IconButton(onClick = { onTransform { MdEdit.linePrefix(it, "# ") } }) {
            Icon(Icons.Filled.Title, contentDescription = "Heading")
        }
        IconButton(onClick = { onTransform { MdEdit.linePrefix(it, "- ") } }) {
            Icon(Icons.Filled.FormatListBulleted, contentDescription = "List")
        }
        IconButton(onClick = { onTransform { MdEdit.linePrefix(it, "- [ ] ") } }) {
            Icon(Icons.Filled.CheckBox, contentDescription = "Checkbox")
        }
        IconButton(onClick = { onTransform { MdEdit.link(it) } }) {
            Icon(Icons.Filled.Link, contentDescription = "Link")
        }
    }
}
