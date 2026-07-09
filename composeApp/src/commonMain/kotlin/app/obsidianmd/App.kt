package app.obsidianmd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_ai
import app.obsidianmd.resources.ai_unavailable
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.cd_settings
import app.obsidianmd.resources.title_ai_chat
import app.obsidianmd.resources.title_note
import app.obsidianmd.resources.title_notes
import app.obsidianmd.resources.title_settings
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.ui.AiChatScreen
import app.obsidianmd.ui.ConflictDialog
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.SettingsScreen
import app.obsidianmd.ui.VaultListScreen
import app.obsidianmd.ui.VaultViewModel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VaultViewModel, settingsVm: SettingsViewModel, aiVm: AiViewModel?) {
    val state by vm.state.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val conflict by vm.pendingConflict.collectAsState()
    val url by settingsVm.url.collectAsState()
    val openRouterKey by settingsVm.openRouterKey.collectAsState()
    val aiEnabled by settingsVm.aiEnabled.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }

    val onHome = !showSettings && !showAi && state.selected == null
    val title = when {
        showSettings -> stringResource(Res.string.title_settings)
        showAi -> stringResource(Res.string.title_ai_chat)
        state.selected != null -> state.selected?.name ?: stringResource(Res.string.title_note)
        !state.atRoot -> state.currentDir.substringAfterLast('/')
        else -> stringResource(Res.string.title_notes)
    }
    val back: (() -> Unit)? = when {
        showSettings -> ({ showSettings = false })
        showAi -> ({ showAi = false })
        state.selected != null -> vm::back
        !state.atRoot -> vm::upFolder
        else -> null
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (back != null) {
                            IconButton(onClick = back) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.cd_back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (onHome) {
                            if (aiEnabled) {
                                TextButton(onClick = { showAi = true }) {
                                    Text(stringResource(Res.string.action_ai))
                                }
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = stringResource(Res.string.cd_settings),
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Surface(Modifier.padding(padding)) {
                when {
                    showSettings -> SettingsScreen(
                        currentUrl = url,
                        onSave = { settingsVm.save(it) },
                        openRouterKey = openRouterKey,
                        onSaveKey = { settingsVm.saveKey(it) },
                        syncStatus = syncStatus,
                        onSync = vm::sync,
                        aiEnabled = aiEnabled,
                        onSetAiEnabled = { settingsVm.setAiEnabled(it) },
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
                        )
                    }
                    showAi && aiVm == null -> AiUnavailable()
                    state.selected == null -> VaultListScreen(
                        state,
                        onOpenFile = vm::open,
                        onOpenFolder = vm::openFolder,
                        query = state.query,
                        results = state.results,
                        onSearch = vm::search,
                    )
                    else -> MarkdownScreen(
                        content = state.content,
                        onSave = { text -> state.selected?.let { vm.saveFile(it.path, text) } },
                    )
                }
                conflict?.let { ConflictDialog(it, onChoose = vm::resolveConflict) }
            }
        }
    }
}

@Composable
private fun AiUnavailable() {
    Column {
        Text(stringResource(Res.string.ai_unavailable))
    }
}
