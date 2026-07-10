package app.obsidianmd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_ai
import app.obsidianmd.resources.action_edit
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.action_discard
import app.obsidianmd.resources.ai_unavailable
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.cd_close_search
import app.obsidianmd.resources.cd_search
import app.obsidianmd.resources.cd_settings
import app.obsidianmd.resources.search_hint
import app.obsidianmd.resources.title_ai_chat
import app.obsidianmd.resources.title_note
import app.obsidianmd.resources.title_notes
import app.obsidianmd.resources.title_settings
import app.obsidianmd.resources.unsaved_message
import app.obsidianmd.resources.unsaved_title
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.ui.AiChatScreen
import app.obsidianmd.ui.ConflictDialog
import app.obsidianmd.ui.decodeImage
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.SettingsScreen
import app.obsidianmd.ui.VaultListScreen
import app.obsidianmd.ui.VaultViewModel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    vm: VaultViewModel,
    settingsVm: SettingsViewModel,
    aiVm: AiViewModel?,
    onPickRepoFromGitHub: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val conflict by vm.pendingConflict.collectAsState()
    val url by settingsVm.url.collectAsState()
    val openRouterKey by settingsVm.openRouterKey.collectAsState()
    val aiEnabled by settingsVm.aiEnabled.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") } // локальный источник правды для поля поиска
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var showUnsaved by remember { mutableStateOf(false) }
    val dirty = editing && draft != state.content
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.selected) { editing = false } // сброс режима правки при смене файла

    val onHome = !showSettings && !showAi && state.selected == null
    val homeSearching = onHome && searching
    val exitSearch = { searching = false; queryText = ""; vm.search("") }
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
        editing -> ({ if (dirty) showUnsaved = true else editing = false }) // «Назад»: защита от потери правок
        state.selected != null -> vm::back
        !state.atRoot -> vm::upFolder
        else -> null
    }

    // Общий скролл-бихейвор: AppBar и поле поиска в списке скрываются/появляются вместе.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (homeSearching) {
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { focus.requestFocus() }
                            TextField(
                                value = queryText,
                                onValueChange = { queryText = it; vm.search(it) },
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
                    scrollBehavior = if (onHome && !searching) scrollBehavior else null,
                    navigationIcon = {
                        if (homeSearching) {
                            IconButton(onClick = exitSearch) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(Res.string.cd_close_search),
                                )
                            }
                        } else if (back != null) {
                            IconButton(onClick = back) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.cd_back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (homeSearching) {
                            // во время поиска — только поле; остальные действия скрыты
                        } else if (onHome) {
                            IconButton(onClick = { searching = true }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(Res.string.cd_search),
                                )
                            }
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
                        } else if (state.selected != null) {
                            if (editing) {
                                IconButton(
                                    onClick = {
                                        state.selected?.let { vm.saveFile(it.path, draft) }
                                        editing = false
                                    },
                                    enabled = dirty, // активна только при несохранённых правках
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = stringResource(Res.string.action_save),
                                    )
                                }
                            } else {
                                IconButton(onClick = { draft = state.content; editing = true }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = stringResource(Res.string.action_edit),
                                    )
                                }
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
                        onPickFromGitHub = onPickRepoFromGitHub,
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
                        scrollBehavior = scrollBehavior,
                    )
                    else -> MarkdownScreen(
                        content = state.content,
                        editing = editing,
                        draft = draft,
                        onDraftChange = { draft = it },
                        files = state.allFiles,
                        loadImage = { path -> decodeImage(vm.bytesOf(path)) },
                        onOpenPath = vm::openPath,
                    )
                }
                conflict?.let { ConflictDialog(it, onChoose = vm::resolveConflict) }
                if (showUnsaved) {
                    AlertDialog(
                        // «Отмена» = закрыть диалог (тап вне / системный back) → остаёмся в редакторе.
                        onDismissRequest = { showUnsaved = false },
                        title = { Text(stringResource(Res.string.unsaved_title)) },
                        text = { Text(stringResource(Res.string.unsaved_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                state.selected?.let { vm.saveFile(it.path, draft) }
                                showUnsaved = false
                                editing = false
                            }) { Text(stringResource(Res.string.action_save)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUnsaved = false; editing = false }) {
                                Text(stringResource(Res.string.action_discard))
                            }
                        },
                    )
                }
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
