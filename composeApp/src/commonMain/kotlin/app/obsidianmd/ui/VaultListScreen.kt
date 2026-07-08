package app.obsidianmd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.sync.SyncResult
import app.obsidianmd.vault.MdFile

@Composable
fun VaultListScreen(
    state: VaultState,
    syncStatus: SyncStatus,
    onSync: () -> Unit,
    onOpen: (MdFile) -> Unit,
    onOpenSettings: () -> Unit,
    query: String,
    results: List<MdFile>,
    onSearch: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick = onSync, modifier = Modifier.padding(16.dp)) { Text("Синхронизировать") }
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Настройки")
        }
        Text(syncStatusText(syncStatus), Modifier.padding(horizontal = 16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val shown = if (query.isBlank()) state.files else results
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Нет файлов") }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown) { file ->
                    Text(
                        file.name,
                        Modifier.fillMaxWidth().clickable { onOpen(file) }.padding(16.dp),
                    )
                }
            }
        }
    }
}

private fun syncStatusText(status: SyncStatus): String = when (status) {
    SyncStatus.Idle -> ""
    SyncStatus.Running -> "Синхронизация…"
    is SyncStatus.Done -> when (val r = status.result) {
        is SyncResult.Cloned -> "Готово: склонировано"
        is SyncResult.UpToDate -> "Актуально"
        is SyncResult.Synced ->
            "Синхронизировано" + if (r.conflictsResolved > 0) " (конфликтов: ${r.conflictsResolved})" else ""
        is SyncResult.Failed -> "Ошибка: ${r.reason}"
    }
}
