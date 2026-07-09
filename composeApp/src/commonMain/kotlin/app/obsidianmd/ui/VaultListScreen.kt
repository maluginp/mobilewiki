package app.obsidianmd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.notes_empty
import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultEntry
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun VaultListScreen(
    state: VaultState,
    onOpenFile: (MdFile) -> Unit,
    onOpenFolder: (VaultEntry) -> Unit,
    query: String,
    results: List<MdFile>,
    onSearch: (String) -> Unit,
) {
    // При поиске показываем найденные файлы (плоско), иначе — содержимое папки.
    val shown: List<VaultEntry> =
        if (query.isBlank()) state.entries
        else results.map { VaultEntry(it.name, it.path, isFolder = false) }

    // Скролл-синхронное скрытие поля поиска: панель сдвигается на offset (в px),
    // верхний отступ списка = высота + offset, поэтому контент движется вместе с ней.
    var barHeightPx by remember { mutableStateOf(0f) }
    var barOffsetPx by remember { mutableStateOf(0f) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                barOffsetPx = (barOffsetPx + available.y).coerceIn(-barHeightPx, 0f)
                return Offset.Zero
            }
        }
    }
    val density = LocalDensity.current
    val topPad = with(density) { (barHeightPx + barOffsetPx).coerceAtLeast(0f).toDp() }

    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.notes_empty))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topPad)) {
                items(shown, key = { it.path }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.displayName()) },
                        leadingContent = {
                            Icon(
                                if (entry.isFolder) Icons.Filled.Folder else Icons.Filled.Description,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (entry.isFolder) onOpenFolder(entry)
                            else onOpenFile(MdFile(entry.name, entry.path))
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
        Surface(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { barHeightPx = it.height.toFloat() }
                .offset { IntOffset(0, barOffsetPx.roundToInt()) },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onSearch,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

private fun VaultEntry.displayName(): String =
    if (isFolder) name else name.removeSuffix(".md")
