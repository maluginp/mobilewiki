package app.obsidianmd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.unit.dp
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_ai
import app.obsidianmd.resources.action_edit
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.action_discard
import app.obsidianmd.resources.ai_open_settings
import app.obsidianmd.resources.ai_unavailable
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.cd_close_search
import app.obsidianmd.resources.cd_search
import app.obsidianmd.resources.cd_settings
import app.obsidianmd.resources.model_search_hint
import app.obsidianmd.resources.nav_brain
import app.obsidianmd.resources.search_hint
import app.obsidianmd.resources.title_ai_chat
import app.obsidianmd.resources.title_model_picker
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
import app.obsidianmd.ui.ModelPickerScreen
import app.obsidianmd.ui.SettingsScreen
import app.obsidianmd.ui.VaultListScreen
import app.obsidianmd.ui.VaultViewModel
import app.obsidianmd.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun App(
    vm: VaultViewModel,
    settingsVm: SettingsViewModel,
    aiVm: AiViewModel?,
    onPickRepoFromGitHub: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val syncStatus = state.syncStatus
    val conflict = state.pendingConflict
    val settings by settingsVm.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var modelSearching by remember { mutableStateOf(false) }
    var modelQuery by remember { mutableStateOf("") }
    var showAi by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") } // локальный источник правды для поля поиска
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var showUnsaved by remember { mutableStateOf(false) }
    val dirty = editing && draft != state.content
    val documents = state.documents
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.selected) { editing = false } // сброс режима правки при смене файла
    LaunchedEffect(editing) { if (editing) vm.loadDocuments() } // подгрузить документы для пикера ссылок

    val onHome = !showSettings && !showAi && state.selected == null
    val homeSearching = onHome && searching
    val exitSearch = { searching = false; queryText = ""; vm.search("") }
    val title = when {
        showModelPicker -> stringResource(Res.string.title_model_picker)
        showSettings -> stringResource(Res.string.title_settings)
        showAi -> stringResource(Res.string.title_ai_chat)
        state.selected != null -> state.selected?.name ?: stringResource(Res.string.title_note)
        !state.atRoot -> state.currentDir.substringAfterLast('/')
        else -> stringResource(Res.string.title_notes)
    }
    val exitModelSearch = { modelSearching = false; modelQuery = "" }
    val back: (() -> Unit)? = when {
        showModelPicker -> ({ if (modelSearching) exitModelSearch() else showModelPicker = false })
        showSettings -> ({ showSettings = false })
        // AI-чат открывается из нижней навигации — назад не нужно (переключение через Bottom Bar).
        editing -> ({ if (dirty) showUnsaved = true else editing = false }) // «Назад»: защита от потери правок
        state.selected != null -> vm::back
        !state.atRoot -> vm::upFolder
        else -> null
    }

    // Общий скролл-бихейвор: AppBar и поле поиска в списке скрываются/появляются вместе.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (homeSearching || (showModelPicker && modelSearching)) {
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { focus.requestFocus() }
                            val value = if (homeSearching) queryText else modelQuery
                            val hint = if (homeSearching) Res.string.search_hint else Res.string.model_search_hint
                            TextField(
                                value = value,
                                onValueChange = {
                                    if (homeSearching) { queryText = it; vm.search(it) } else modelQuery = it
                                },
                                placeholder = { Text(stringResource(hint)) },
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
                        if (homeSearching || (showModelPicker && modelSearching)) {
                            IconButton(onClick = { if (homeSearching) exitSearch() else exitModelSearch() }) {
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
                        if (homeSearching || (showModelPicker && modelSearching)) {
                            // во время поиска — только поле; остальные действия скрыты
                        } else if (showModelPicker) {
                            IconButton(onClick = { modelSearching = true }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(Res.string.cd_search),
                                )
                            }
                        } else if (onHome) {
                            IconButton(onClick = { searching = true }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(Res.string.cd_search),
                                )
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
            bottomBar = {
                // Нижняя навигация появляется только при включённом AI: Brain (заметки) ↔ AI.
                // Скрываем, пока открыта клавиатура, — иначе её место остаётся зазором над клавиатурой.
                if (settings.aiEnabled && !showSettings && !WindowInsets.isImeVisible) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = !showAi,
                            onClick = { showAi = false },
                            icon = { Icon(Icons.Filled.Psychology, contentDescription = null) },
                            label = { Text(stringResource(Res.string.nav_brain)) },
                        )
                        NavigationBarItem(
                            selected = showAi,
                            onClick = { showAi = true },
                            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            label = { Text(stringResource(Res.string.action_ai)) },
                        )
                    }
                }
            },
        ) { padding ->
            Surface(Modifier.padding(padding)) {
                when {
                    showModelPicker -> ModelPickerScreen(
                        models = settings.models,
                        loading = settings.modelsLoading,
                        selected = settings.aiModel,
                        query = modelQuery,
                        onSelect = { settingsVm.setAiModel(it); exitModelSearch(); showModelPicker = false },
                        onRefresh = settingsVm::reloadModels,
                    )
                    showSettings -> SettingsScreen(
                        state = settings,
                        onSave = { settingsVm.save(it) },
                        onSaveKey = { settingsVm.saveKey(it) },
                        onSetAiEnabled = { settingsVm.setAiEnabled(it) },
                        onEditModel = { settingsVm.ensureModels(); showModelPicker = true },
                        syncStatus = syncStatus,
                        onSync = vm::sync,
                        onPickFromGitHub = onPickRepoFromGitHub,
                    )
                    showAi && aiVm != null -> {
                        val aiState by aiVm.state.collectAsState()
                        AiChatScreen(
                            messages = aiState.messages,
                            status = aiState.status,
                            pendingWrite = aiState.pendingWrite,
                            onSend = aiVm::send,
                            onApprove = aiVm::approveWrite,
                            onReject = aiVm::rejectWrite,
                            files = state.allFiles,
                            onOpenFile = { path -> showAi = false; vm.openPath(path) },
                        )
                    }
                    showAi && aiVm == null -> AiUnavailable(
                        onOpenSettings = { showAi = false; showSettings = true },
                    )
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
                        documents = documents,
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
private fun AiUnavailable(onOpenSettings: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(
            stringResource(Res.string.ai_unavailable),
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(Res.string.ai_open_settings))
        }
    }
}
