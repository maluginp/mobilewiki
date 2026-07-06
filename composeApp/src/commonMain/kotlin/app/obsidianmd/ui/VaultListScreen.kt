package app.obsidianmd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.vault.MdFile

@Composable
fun VaultListScreen(state: VaultState, onOpen: (MdFile) -> Unit) {
    if (state.files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет файлов")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.files) { file ->
            Text(
                file.name,
                Modifier.fillMaxWidth().clickable { onOpen(file) }.padding(16.dp),
            )
        }
    }
}
