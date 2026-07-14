package app.obsidianmd.note.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.ui.unit.dp
import app.obsidianmd.note.domain.EditState
import app.obsidianmd.note.domain.MdEdit

private val BAR_HEIGHT = 40.dp
private val BTN = 40.dp
private val ICON = 20.dp

/** Панель форматирования: каждая кнопка отдаёт наверх трансформацию над текущим EditState. */
@Composable
internal fun EditorToolbar(
    onTransform: ((EditState) -> EditState) -> Unit,
    onPickDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(BAR_HEIGHT).horizontalScroll(rememberScrollState())) {
        Btn({ onTransform { MdEdit.wrapInline(it, "**") } }) { Icon(Icons.Filled.FormatBold, "Bold", Modifier.size(ICON)) }
        Btn({ onTransform { MdEdit.wrapInline(it, "*") } }) { Icon(Icons.Filled.FormatItalic, "Italic", Modifier.size(ICON)) }
        Btn({ onTransform { MdEdit.linePrefix(it, "# ") } }) { Icon(Icons.Filled.Title, "Heading", Modifier.size(ICON)) }
        Btn({ onTransform { MdEdit.linePrefix(it, "- ") } }) { Icon(Icons.Filled.FormatListBulleted, "List", Modifier.size(ICON)) }
        Btn({ onTransform { MdEdit.linePrefix(it, "- [ ] ") } }) { Icon(Icons.Filled.CheckBox, "Checkbox", Modifier.size(ICON)) }
        Btn({ onTransform { MdEdit.link(it) } }) { Icon(Icons.Filled.Link, "Link", Modifier.size(ICON)) }
        Btn(onPickDocument) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Link to note", Modifier.size(ICON)) }
    }
}

@Composable
private fun Btn(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(BTN)) { content() }
}
