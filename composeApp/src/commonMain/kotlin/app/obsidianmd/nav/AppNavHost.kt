package app.obsidianmd.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.obsidianmd.ai.AiPresentationProvider
import app.obsidianmd.onboarding.OnboardingPresentationProvider
import app.obsidianmd.note.NotePresentationProvider
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_ai
import app.obsidianmd.resources.detail_empty
import app.obsidianmd.resources.nav_brain
import app.obsidianmd.resources.title_notes
import app.obsidianmd.settings.SettingsPresentationProvider
import app.obsidianmd.ui.ConflictDialog
import app.obsidianmd.ui.SyncStatus
import app.obsidianmd.ui.VaultViewModel
import app.obsidianmd.ui.decodeImage
import app.obsidianmd.ui.syncStatusText
import app.obsidianmd.vault.VaultPresentationProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private fun androidx.navigation3.runtime.NavBackStack<NavKey>.resetTo(items: List<NavKey>) {
    clear(); addAll(items)
}

/**
 * Единый хост навигации. Бэкстек — источник правды для истории: онбординг, папки, заметки,
 * настройки и AI живут в одном стеке. TopAppBar — у каждого экрана свой; общую нижнюю навигацию
 * (Brain ↔ AI) рисует сам хост по текущему маршруту — экраны фичей о ней не знают.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppNavHost(initialStack: List<Route>) {
    val backStack = rememberNavBackStack(navSavedStateConfiguration, *initialStack.toTypedArray())

    val vm: VaultViewModel = koinViewModel()
    val settingsPresentation = koinInject<SettingsPresentationProvider>()
    val auth = koinInject<OnboardingPresentationProvider>()
    val vaultPresentation = koinInject<VaultPresentationProvider>()
    val notePresentation = koinInject<NotePresentationProvider>()
    val ai = koinInject<AiPresentationProvider>()

    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
      // Общий слот нижней навигации живёт только здесь: бар выбирается по верхушке бэкстека.
      // ponytail: экраны с баром сохраняют свой нижний inset — над баром остаётся полоска в
      // высоту nav-bar. Убрать её = сообщать экрану о наличии бара, т.е. вернуть связанность.
      Scaffold(
        bottomBar = {
            when (backStack.lastOrNull()) {
                is Route.AiChat -> BrainAiBottomBar(onAi = true, ai, backStack)
                is Route.VaultList, is Route.Note -> BrainAiBottomBar(onAi = false, ai, backStack)
                else -> {}
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
      ) { pad ->
        NavDisplay(
            modifier = Modifier.padding(pad),
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            // На широких экранах список + заметка показываются рядом (list-detail).
            sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>()),
            entryProvider = entryProvider {
                entry<Route.Onboarding> { key ->
                    auth.Onboarding(
                        startAt = key.startAt,
                        onFinished = {
                            backStack.resetTo(listOf(Route.VaultList()))
                            vm.sync()
                        },
                    )
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
                        onCreateNote = { name -> vm.createNote(name) { path -> backStack.add(Route.Note(path)) } },
                        onCreateFolder = vm::createFolder,
                    )
                }
                entry<Route.Note>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    LaunchedEffect(key.path) { vm.openNote(key.path); vm.loadDocuments() }
                    notePresentation.NoteScreen(
                        title = key.path.substringAfterLast('/'),
                        content = state.content,
                        files = state.allFiles,
                        documents = state.documents,
                        loadImage = { path -> decodeImage(vm.bytesOf(path)) },
                        onOpenPath = { backStack.add(Route.Note(it)) },
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onSave = { vm.saveFile(key.path, it) },
                    )
                }
                entry<Route.Settings> {
                    settingsPresentation.Screen(
                        syncing = state.syncStatus is SyncStatus.Running,
                        syncStatusText = syncStatusText(state.syncStatus),
                        onSync = vm::sync,
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onChangeRepository = { backStack.add(Route.ChangeRepo) },
                        aiSection = { ai.SettingsSection(onEditModel = { backStack.add(Route.ModelPicker) }) },
                    )
                }
                entry<Route.ChangeRepo> {
                    settingsPresentation.ChangeRepoScreen(
                        onPickFromGitHub = { backStack.resetTo(stackForChangeRepo()) },
                        onConnectManually = { backStack.resetTo(stackForChangeRepoManual()) },
                        onNavigateBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Route.ModelPicker> {
                    ai.ModelPicker(onNavigateBack = { backStack.removeLastOrNull() })
                }
                entry<Route.AiChat> {
                    ai.Chat(
                        onOpenFile = { path -> backStack.add(Route.Note(path)) },
                        onOpenSettings = { backStack.removeLastOrNull(); backStack.add(Route.Settings) },
                    )
                }
            },
        )
      }
        // Конфликт синхронизации может всплыть во время sync на любом экране — диалог поверх всего.
        state.pendingConflict?.let { ConflictDialog(it, onChoose = vm::resolveConflict) }
    }
}

/** Общая нижняя навигация Brain ↔ AI: видна при aiEnabled и скрыта под клавиатурой. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainAiBottomBar(
    onAi: Boolean,
    ai: AiPresentationProvider,
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
) {
    if (!ai.aiEnabled() || WindowInsets.isImeVisible) return
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
