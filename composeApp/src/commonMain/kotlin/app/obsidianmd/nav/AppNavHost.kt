package app.obsidianmd.nav

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
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.auth.AuthState
import app.obsidianmd.auth.AuthViewModel
import app.obsidianmd.auth.RepoPickerViewModel
import app.obsidianmd.auth.RepoValidationViewModel
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_ai
import app.obsidianmd.resources.action_discard
import app.obsidianmd.resources.action_edit
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.ai_open_settings
import app.obsidianmd.resources.ai_unavailable
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.cd_close_search
import app.obsidianmd.resources.cd_search
import app.obsidianmd.resources.cd_settings
import app.obsidianmd.resources.detail_empty
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
import app.obsidianmd.settings.SettingsState
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.ui.AiChatScreen
import app.obsidianmd.ui.ConflictDialog
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.ModelPickerScreen
import app.obsidianmd.ui.SettingsScreen
import app.obsidianmd.ui.SyncStatus
import app.obsidianmd.ui.VaultViewModel
import app.obsidianmd.ui.LoginScreen
import app.obsidianmd.ui.WelcomeScreen
import app.obsidianmd.ui.RepoPickerScreen
import app.obsidianmd.ui.ManualUrlScreen
import app.obsidianmd.ui.RepoValidationScreen
import app.obsidianmd.ui.decodeImage
import app.obsidianmd.vault.VaultPresentationProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private fun androidx.navigation3.runtime.NavBackStack<NavKey>.resetTo(items: List<NavKey>) {
    clear(); addAll(items)
}

private val Route.isOnboarding: Boolean
    get() = this is Route.Login || this is Route.RepoPicker ||
        this is Route.RepoManualUrl || this is Route.RepoValidate

/**
 * Единый хост навигации. Бэкстек — источник правды для истории: онбординг, папки,
 * заметки, настройки и AI живут в одном стеке. TopAppBar и нижняя навигация зависят
 * от верхнего маршрута; режим правки и поиск — транзитный UI-стейт хоста.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppNavHost(initialStack: List<Route>) {
    val backStack = rememberNavBackStack(navSavedStateConfiguration, *initialStack.toTypedArray())
    val top = backStack.lastOrNull() as? Route

    val vm: VaultViewModel = koinViewModel()
    val settingsVm: SettingsViewModel = koinViewModel()
    val authVm: AuthViewModel = koinViewModel()

    val state by vm.state.collectAsState()
    val settings by settingsVm.state.collectAsState()
    val authState by authVm.state.collectAsState()

    // Успешный логин с экрана онбординга → пересобрать стек (список или выбор репо).
    LaunchedEffect(authState) {
        if (authState is AuthState.Success && top?.isOnboarding == true) {
            backStack.resetTo(startStack(hasToken = true, hasRepo = settings.url.isNotBlank()))
        }
    }

    // Транзитный UI-стейт (не навигация): поиск в списке/пикере, режим правки заметки.
    var searching by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") }
    var modelSearching by remember { mutableStateOf(false) }
    var modelQuery by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var showUnsaved by remember { mutableStateOf(false) }
    val dirty = editing && draft != state.content

    val onVaultList = top is Route.VaultList
    val onNote = top is Route.Note
    val onModelPicker = top is Route.ModelPicker
    val homeSearching = onVaultList && searching
    val exitSearch = { searching = false; queryText = ""; vm.search("") }
    val exitModelSearch = { modelSearching = false; modelQuery = "" }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val conflict = state.pendingConflict
    val aiVm = rememberAiViewModel(settings)

    // Стрелка «назад» в AppBar (для маршрутов, где она нужна).
    val back: (() -> Unit)? = when {
        homeSearching || (onModelPicker && modelSearching) -> null // показываем крестик, не стрелку
        onNote && editing -> ({ if (dirty) showUnsaved = true else editing = false })
        backStack.size > 1 -> ({ backStack.removeLastOrNull(); Unit })
        else -> null
    }

    val title = when (top) {
        is Route.ModelPicker -> stringResource(Res.string.title_model_picker)
        is Route.Settings -> stringResource(Res.string.title_settings)
        is Route.AiChat -> stringResource(Res.string.title_ai_chat)
        is Route.Note -> (top.path.substringAfterLast('/')).ifBlank { stringResource(Res.string.title_note) }
        is Route.VaultList -> if (top.dir.isBlank()) stringResource(Res.string.title_notes)
            else top.dir.substringAfterLast('/')
        else -> ""
    }

    Scaffold(
        topBar = {
            if (top?.isOnboarding == true || top == null) return@Scaffold
            TopAppBar(
                title = {
                    if (homeSearching || (onModelPicker && modelSearching)) {
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
                scrollBehavior = if (onVaultList && !searching) scrollBehavior else null,
                navigationIcon = {
                    if (homeSearching || (onModelPicker && modelSearching)) {
                        IconButton(onClick = { if (homeSearching) exitSearch() else exitModelSearch() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_close_search))
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
                    if (homeSearching || (onModelPicker && modelSearching)) {
                        // во время поиска — только поле
                    } else if (onModelPicker) {
                        IconButton(onClick = { modelSearching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.cd_search))
                        }
                    } else if (onVaultList) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.cd_search))
                        }
                        IconButton(onClick = { backStack.add(Route.Settings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(Res.string.cd_settings))
                        }
                    } else if (onNote) {
                        if (editing) {
                            IconButton(
                                onClick = {
                                    (top as? Route.Note)?.let { vm.saveFile(it.path, draft) }
                                    editing = false
                                },
                                enabled = dirty,
                            ) { Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.action_save)) }
                        } else {
                            IconButton(onClick = { draft = state.content; editing = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.action_edit))
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Brain ↔ AI: видна при aiEnabled на списке/заметке/чате, скрыта под клавиатурой и в настройках/пикере.
            val showBottom = settings.aiEnabled && !WindowInsets.isImeVisible &&
                (onVaultList || onNote || top is Route.AiChat)
            if (showBottom) {
                val onAi = top is Route.AiChat
                NavigationBar {
                    NavigationBarItem(
                        selected = !onAi,
                        onClick = { if (onAi) backStack.removeLastOrNull() },
                        icon = { Icon(Icons.Filled.Psychology, contentDescription = null) },
                        label = { Text(stringResource(Res.string.nav_brain)) },
                    )
                    NavigationBarItem(
                        selected = onAi,
                        onClick = { if (!onAi) backStack.add(Route.AiChat) },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                        label = { Text(stringResource(Res.string.action_ai)) },
                    )
                }
            }
        },
    ) { padding ->
        Surface(Modifier.padding(padding)) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                // На широких экранах список + заметка показываются рядом (list-detail).
                sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>()),
                entryProvider = entryProvider {
                    entry<Route.Login> {
                        // Idle — приветствие с кнопкой входа; после старта авторизации тот же
                        // маршрут показывает код/ожидание (без лишнего промежуточного экрана).
                        if (authState is AuthState.Idle) {
                            WelcomeScreen(onSignIn = authVm::login)
                        } else {
                            val uriHandler = LocalUriHandler.current
                            LoginScreen(
                                state = authState,
                                onLogin = authVm::login,
                                onOpenUrl = { uriHandler.openUri(it) },
                            )
                        }
                    }
                    entry<Route.RepoPicker> { RepoPickerRoute(backStack) }
                    entry<Route.RepoManualUrl> {
                        ManualUrlScreen(
                            onSubmit = { url -> backStack.add(Route.RepoValidate(url)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Route.RepoValidate> { key ->
                        val validationVm: RepoValidationViewModel = koinViewModel()
                        LaunchedEffect(key.url) { validationVm.validate(key.url) }
                        val vs by validationVm.state.collectAsState()
                        RepoValidationScreen(
                            state = vs,
                            onContinue = {
                                settingsVm.save(key.url)
                                backStack.resetTo(stackAfterRepoChosen())
                                vm.sync()
                            },
                            onRetry = { validationVm.validate(key.url) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Route.VaultList>(
                        metadata = ListDetailSceneStrategy.listPane {
                            // Пустой detail-пейн на широком экране, пока заметка не выбрана.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(Res.string.detail_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    ) { key ->
                        LaunchedEffect(key.dir) { vm.openDir(key.dir) }
                        koinInject<VaultPresentationProvider>().ListScreen(
                            entries = state.entries,
                            loading = state.loading,
                            refreshing = state.syncStatus is SyncStatus.Running,
                            query = state.query,
                            results = state.results,
                            onOpenFile = { backStack.add(Route.Note(it.path)) },
                            onOpenFolder = { backStack.add(Route.VaultList(it.path)) },
                            onRefresh = vm::sync,
                            scrollBehavior = scrollBehavior,
                        )
                    }
                    entry<Route.Note>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                        LaunchedEffect(key.path) { editing = false; vm.openNote(key.path) }
                        LaunchedEffect(editing) { if (editing) vm.loadDocuments() }
                        MarkdownScreen(
                            content = state.content,
                            editing = editing,
                            draft = draft,
                            onDraftChange = { draft = it },
                            files = state.allFiles,
                            documents = state.documents,
                            loadImage = { path -> decodeImage(vm.bytesOf(path)) },
                            onOpenPath = { backStack.add(Route.Note(it)) },
                        )
                    }
                    entry<Route.Settings> {
                        SettingsScreen(
                            state = settings,
                            onSave = { settingsVm.save(it) },
                            onSaveKey = { settingsVm.saveKey(it) },
                            onSetAiEnabled = { settingsVm.setAiEnabled(it) },
                            onEditModel = { settingsVm.ensureModels(); backStack.add(Route.ModelPicker) },
                            onSetProvider = { settingsVm.setProvider(it) },
                            onSetCustomBaseUrl = { settingsVm.setCustomBaseUrl(it) },
                            syncStatus = state.syncStatus,
                            onSync = vm::sync,
                            onPickFromGitHub = { backStack.resetTo(stackForChangeRepo()) },
                        )
                    }
                    entry<Route.ModelPicker> {
                        ModelPickerScreen(
                            models = settings.models,
                            loading = settings.modelsLoading,
                            selected = settings.aiModel,
                            query = modelQuery,
                            onSelect = { settingsVm.setAiModel(it); exitModelSearch(); backStack.removeLastOrNull() },
                            onRefresh = settingsVm::reloadModels,
                            showFilters = settings.provider.supportsModelFilters,
                        )
                    }
                    entry<Route.AiChat> {
                        if (aiVm != null) {
                            val aiState by aiVm.state.collectAsState()
                            AiChatScreen(
                                messages = aiState.messages,
                                status = aiState.status,
                                pendingWrite = aiState.pendingWrite,
                                onSend = aiVm::send,
                                onApprove = aiVm::approveWrite,
                                onReject = aiVm::rejectWrite,
                                files = state.allFiles,
                                onOpenFile = { path -> backStack.add(Route.Note(path)) },
                            )
                        } else {
                            AiUnavailable(onOpenSettings = {
                                backStack.removeLastOrNull(); backStack.add(Route.Settings)
                            })
                        }
                    }
                },
            )
            conflict?.let { ConflictDialog(it, onChoose = vm::resolveConflict) }
            if (showUnsaved) {
                AlertDialog(
                    onDismissRequest = { showUnsaved = false },
                    title = { Text(stringResource(Res.string.unsaved_title)) },
                    text = { Text(stringResource(Res.string.unsaved_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            (top as? Route.Note)?.let { vm.saveFile(it.path, draft) }
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

/** Выбор репозитория: подгрузка списка, переход к валидации при выборе. */
@Composable
private fun RepoPickerRoute(backStack: androidx.navigation3.runtime.NavBackStack<NavKey>) {
    val pickerVm: RepoPickerViewModel = koinViewModel()
    LaunchedEffect(Unit) { pickerVm.load() }
    val pickerState by pickerVm.state.collectAsState()
    RepoPickerScreen(
        state = pickerState,
        // Навигация прямо из выбора — не через залипающий StateFlow picked
        // (иначе повторный вход авто-выбирал прежний репозиторий).
        onChoose = { url -> backStack.add(Route.RepoValidate(url)) },
        onRetry = pickerVm::load,
        onEnterManually = { backStack.add(Route.RepoManualUrl) },
        onBack = if (backStack.size > 1) ({ backStack.removeLastOrNull(); Unit }) else null,
    )
}

/**
 * AI-ViewModel, привязанный к провайдеру/модели/URL: смена любого пересоздаёт клиент.
 * null — если ключ не задан или AI выключен (тогда экран чата покажет заглушку).
 */
@Composable
private fun rememberAiViewModel(settings: SettingsState): AiViewModel? {
    val apiKeyStore: ApiKeyStore = koinInject()
    val provider = settings.provider
    val aiModel = settings.aiModel
    val aiKey = remember(settings.aiEnabled, aiModel, provider, settings.apiKey) {
        apiKeyStore.getKey(provider.id)?.takeIf { it.isNotBlank() && settings.aiEnabled }
    }
    val chatUrl = provider.resolvedChatUrl(settings.customBaseUrl)
    return aiKey?.let { key ->
        koinViewModel(key = "${provider.id}:$aiModel:$chatUrl") { parametersOf(aiModel, key, chatUrl) }
    }
}

@Composable
private fun AiUnavailable(onOpenSettings: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(Res.string.ai_unavailable), color = MaterialTheme.colorScheme.error)
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(Res.string.ai_open_settings))
        }
    }
}
