package app.obsidianmd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.ui.ConflictDialog
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.SettingsScreen
import app.obsidianmd.ui.VaultListScreen
import app.obsidianmd.ui.VaultViewModel

@Composable
fun App(vm: VaultViewModel, settingsVm: SettingsViewModel) {
    val state by vm.state.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val conflict by vm.pendingConflict.collectAsState()
    val url by settingsVm.url.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }
    MaterialTheme {
        Surface {
            when {
                showSettings -> SettingsScreen(
                    currentUrl = url,
                    onSave = { settingsVm.save(it); showSettings = false },
                    onBack = { showSettings = false },
                )
                state.selected == null -> VaultListScreen(
                    state, syncStatus,
                    onSync = vm::sync,
                    onOpen = vm::open,
                    onOpenSettings = { showSettings = true },
                )
                else -> MarkdownScreen(
                    content = state.content,
                    onBack = vm::back,
                    onSave = { text -> state.selected?.let { vm.saveFile(it.path, text) } },
                )
            }
            conflict?.let { ConflictDialog(it, onChoose = vm::resolveConflict) }
        }
    }
}
