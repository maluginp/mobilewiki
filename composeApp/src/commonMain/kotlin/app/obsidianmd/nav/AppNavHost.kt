package app.obsidianmd.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.auth.AuthPresentationProvider
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_ai
import app.obsidianmd.resources.ai_open_settings
import app.obsidianmd.resources.ai_unavailable
import app.obsidianmd.resources.detail_empty
import app.obsidianmd.resources.nav_brain
import app.obsidianmd.resources.title_notes
import app.obsidianmd.settings.SettingsState
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.ui.AiChatScreen
import app.obsidianmd.ui.ConflictDialog
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.ModelPickerScreen
import app.obsidianmd.ui.SettingsScreen
import app.obsidianmd.ui.SyncStatus
import app.obsidianmd.ui.VaultViewModel
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
 * Единый хост навигации. Бэкстек — источник правды для истории: онбординг, папки, заметки,
 * настройки и AI живут в одном стеке. TopAppBar — у каждого экрана свой; хост только
 * прокидывает общий слот нижней навигации (Brain ↔ AI) экранам, где она нужна.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppNavHost(initialStack: List<Route>) {
    val backStack = rememberNavBackStack(navSavedStateConfiguration, *initialStack.toTypedArray())

    val vm: VaultViewModel = koinViewModel()
    val settingsVm: SettingsViewModel = koinViewModel()
    val auth = koinInject<AuthPresentationProvider>()
    val vaultPresentation = koinInject<VaultPresentationProvider>()

    val state by vm.state.collectAsState()
    val settings by settingsVm.state.collectAsState()
    val aiVm = rememberAiViewModel(settings)

    Box(Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            // На широких экранах список + заметка показываются рядом (list-detail).
            sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>()),
            entryProvider = entryProvider {
                entry<Route.Login> {
                    OnboardingContainer {
                        auth.Login(onSignedIn = {
                            backStack.resetTo(startStack(hasToken = true, hasRepo = settings.url.isNotBlank()))
                        })
                    }
                }
                entry<Route.RepoPicker> {
                    OnboardingContainer {
                        auth.RepoPicker(
                            onChosen = { url -> backStack.add(Route.RepoValidate(url)) },
                            onEnterManually = { backStack.add(Route.RepoManualUrl) },
                            onBack = if (backStack.size > 1) ({ backStack.removeLastOrNull(); Unit }) else null,
                        )
                    }
                }
                entry<Route.RepoManualUrl> {
                    OnboardingContainer {
                        auth.ManualUrl(
                            onSubmit = { url -> backStack.add(Route.RepoValidate(url)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                }
                entry<Route.RepoValidate> { key ->
                    OnboardingContainer {
                        auth.RepoValidate(
                            url = key.url,
                            onContinue = {
                                settingsVm.save(key.url)
                                backStack.resetTo(stackAfterRepoChosen())
                                vm.sync()
                            },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                }
                entry<Route.VaultList>(
                    metadata = ListDetailSceneStrategy.listPane {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.detail_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                ) { key ->
                    LaunchedEffect(key.dir) { vm.openDir(key.dir) }
                    val title = if (key.dir.isBlank()) stringResource(Res.string.title_notes)
                        else key.dir.substringAfterLast('/')
                    vaultPresentation.ListScreen(
                        title = title,
                        entries = state.entries,
                        loading = state.loading,
                        refreshing = state.syncStatus is SyncStatus.Running,
                        query = state.query,
                        results = state.results,
                        onQueryChange = vm::search,
                        onOpenFile = { backStack.add(Route.Note(it.path)) },
                        onOpenFolder = { backStack.add(Route.VaultList(it.path)) },
                        onRefresh = vm::sync,
                        onOpenSettings = { backStack.add(Route.Settings) },
                        onBack = if (backStack.size > 1) ({ backStack.removeLastOrNull(); Unit }) else null,
                        bottomBar = { BrainAiBottomBar(onAi = false, settings, backStack) },
                    )
                }
                entry<Route.Note>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    LaunchedEffect(key.path) { vm.openNote(key.path); vm.loadDocuments() }
                    MarkdownScreen(
                        title = key.path.substringAfterLast('/'),
                        content = state.content,
                        files = state.allFiles,
                        documents = state.documents,
                        loadImage = { path -> decodeImage(vm.bytesOf(path)) },
                        onOpenPath = { backStack.add(Route.Note(it)) },
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onSave = { vm.saveFile(key.path, it) },
                        bottomBar = { BrainAiBottomBar(onAi = false, settings, backStack) },
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
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onPickFromGitHub = { backStack.resetTo(stackForChangeRepo()) },
                    )
                }
                entry<Route.ModelPicker> {
                    ModelPickerScreen(
                        models = settings.models,
                        loading = settings.modelsLoading,
                        selected = settings.aiModel,
                        onSelect = { settingsVm.setAiModel(it); backStack.removeLastOrNull() },
                        onRefresh = settingsVm::reloadModels,
                        onNavigateBack = { backStack.removeLastOrNull() },
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
                            bottomBar = { BrainAiBottomBar(onAi = true, settings, backStack) },
                        )
                    } else {
                        Box(Modifier.safeDrawingPadding()) {
                            AiUnavailable(onOpenSettings = {
                                backStack.removeLastOrNull(); backStack.add(Route.Settings)
                            })
                        }
                    }
                }
            },
        )
        // Конфликт синхронизации может всплыть во время sync на любом экране — диалог поверх всего.
        state.pendingConflict?.let { ConflictDialog(it, onChoose = vm::resolveConflict) }
    }
}

/** Онбординг-экраны рисуются на всю ширину с учётом системных вставок (нет своего Scaffold). */
@Composable
private fun OnboardingContainer(content: @Composable () -> Unit) {
    Box(Modifier.safeDrawingPadding()) { content() }
}

/** Общая нижняя навигация Brain ↔ AI: видна при aiEnabled и скрыта под клавиатурой. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainAiBottomBar(
    onAi: Boolean,
    settings: SettingsState,
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
) {
    if (!settings.aiEnabled || WindowInsets.isImeVisible) return
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
