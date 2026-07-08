package app.obsidianmd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.obsidianmd.ai.AiStatus
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.ui.AiChatScreen
import app.obsidianmd.ui.ConflictDialog
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.SettingsScreen
import app.obsidianmd.ui.VaultListScreen
import app.obsidianmd.ui.VaultViewModel

@Composable
fun App(vm: VaultViewModel, settingsVm: SettingsViewModel, aiVm: AiViewModel?) {
    val state by vm.state.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val conflict by vm.pendingConflict.collectAsState()
    val url by settingsVm.url.collectAsState()
    val openRouterKey by settingsVm.openRouterKey.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }
    MaterialTheme {
        Surface {
            when {
                showSettings -> SettingsScreen(
                    currentUrl = url,
                    onSave = { settingsVm.save(it) },
                    openRouterKey = openRouterKey,
                    onSaveKey = { settingsVm.saveKey(it) },
                    onBack = { showSettings = false },
                )
                showAi && aiVm != null -> {
                    val messages by aiVm.messages.collectAsState()
                    val aiStatus by aiVm.status.collectAsState()
                    val pending by aiVm.pendingWrite.collectAsState()
                    AiChatScreen(
                        messages = messages,
                        status = aiStatus,
                        pendingWrite = pending,
                        onSend = aiVm::send,
                        onApprove = aiVm::approveWrite,
                        onReject = aiVm::rejectWrite,
                        onBack = { showAi = false },
                    )
                }
                showAi && aiVm == null -> AiUnavailable(onBack = { showAi = false })
                state.selected == null -> VaultListScreen(
                    state, syncStatus,
                    onSync = vm::sync,
                    onOpen = vm::open,
                    onOpenSettings = { showSettings = true },
                    query = state.query,
                    results = state.results,
                    onSearch = vm::search,
                    onOpenAi = { showAi = true },
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

@Composable
private fun AiUnavailable(onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column {
        androidx.compose.material3.TextButton(onClick = onBack) { Text("← Назад") }
        Text("Не задан ключ OpenRouter — добавьте его в настройках.")
    }
}
