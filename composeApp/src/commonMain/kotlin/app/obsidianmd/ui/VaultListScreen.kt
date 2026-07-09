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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    state: VaultState,
    onOpenFile: (MdFile) -> Unit,
    onOpenFolder: (VaultEntry) -> Unit,
    query: String,
    results: List<MdFile>,
    onSearch: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // При поиске показываем найденные файлы (плоско), иначе — содержимое папки.
    val shown: List<VaultEntry> =
        if (query.isBlank()) state.entries
        else results.map { VaultEntry(it.name, it.path, isFolder = false) }

    // Поле поиска скрывается синхронно с AppBar: тот же scrollBehavior задаёт долю
    // сворачивания (0 — раскрыто, 1 — спрятано). Панель сдвигаем на эту долю высоты,
    // а верхний отступ списка ужимаем на неё же — контент движется вместе с панелью.
    var barHeightPx by remember { mutableStateOf(0f) }
    val hidden = barHeightPx * scrollBehavior.state.collapsedFraction
    val density = LocalDensity.current
    val topPad = with(density) { (barHeightPx - hidden).coerceAtLeast(0f).toDp() }

    Box(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
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
                .offset { IntOffset(0, -hidden.roundToInt()) },
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
