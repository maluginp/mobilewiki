package app.obsidianmd.vault.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.obsidianmd.vault.presentation.resources.Res
import app.obsidianmd.vault.presentation.resources.notes_empty
import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultEntry
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    state: VaultState,
    onOpenFile: (MdFile) -> Unit,
    onOpenFolder: (VaultEntry) -> Unit,
    query: String,
    results: List<MdFile>,
    scrollBehavior: TopAppBarScrollBehavior,
    onRefresh: () -> Unit,
) {
    // При поиске показываем найденные файлы (плоско, по всему vault), иначе — содержимое папки.
    val shown: List<VaultEntry> =
        if (query.isBlank()) state.entries
        else results.map { VaultEntry(it.name, it.path, isFolder = false) }

    // Pull-to-refresh синхронизирует vault (git pull), затем перечитывает текущую папку.
    // При поиске отключаем — жест конфликтует с прокруткой результатов и синк тут неуместен.
    val refreshing = state.syncStatus is SyncStatus.Running
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator()
                else Text(stringResource(Res.string.notes_empty))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.path }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.name) },
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
    }
}
