package app.obsidianmd.vault.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.cd_close_search
import app.obsidianmd.resources.cd_search
import app.obsidianmd.resources.cd_settings
import app.obsidianmd.resources.notes_empty
import app.obsidianmd.resources.search_hint
import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultEntry
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultListScreen(
    title: String,
    entries: List<VaultEntry>,
    loading: Boolean,
    refreshing: Boolean,
    query: String,
    results: List<MdFile>,
    onQueryChange: (String) -> Unit,
    onOpenFile: (MdFile) -> Unit,
    onOpenFolder: (VaultEntry) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: (() -> Unit)?,
    bottomBar: @Composable () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    val exitSearch = { searching = false; onQueryChange("") }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text(stringResource(Res.string.search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    } else {
                        Text(title)
                    }
                },
                scrollBehavior = if (searching) null else scrollBehavior,
                navigationIcon = {
                    if (searching) {
                        IconButton(onClick = exitSearch) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_close_search))
                        }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.cd_back),
                            )
                        }
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.cd_search))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(Res.string.cd_settings))
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        // При поиске показываем найденные файлы (плоско, по всему vault), иначе — содержимое папки.
        val shown: List<VaultEntry> =
            if (query.isBlank()) entries
            else results.map { VaultEntry(it.name, it.path, isFolder = false) }

        // Pull-to-refresh синхронизирует vault (git pull), затем перечитывает текущую папку.
        // При поиске отключаем — жест конфликтует с прокруткой результатов и синк тут неуместен.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding)
                .then(if (searching) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        ) {
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator()
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
}
