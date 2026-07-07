package app.obsidianmd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.obsidianmd.ui.ConflictDialog
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.VaultListScreen
import app.obsidianmd.ui.VaultViewModel

@Composable
fun App(vm: VaultViewModel) {
    val state by vm.state.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val conflict by vm.pendingConflict.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    MaterialTheme {
        Surface {
            if (state.selected == null) {
                VaultListScreen(state, syncStatus, onSync = vm::sync, onOpen = vm::open)
            } else {
                MarkdownScreen(state.content, onBack = vm::back)
            }
            conflict?.let { ConflictDialog(it, onChoose = vm::resolveConflict) }
        }
    }
}
