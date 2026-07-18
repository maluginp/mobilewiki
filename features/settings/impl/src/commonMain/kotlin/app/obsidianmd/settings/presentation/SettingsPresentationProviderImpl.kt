package app.obsidianmd.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.obsidianmd.settings.SettingsPresentationProvider
import org.koin.compose.viewmodel.koinViewModel

internal class SettingsPresentationProviderImpl : SettingsPresentationProvider {
    @Composable
    override fun Screen(
        syncing: Boolean,
        syncStatusText: String,
        onSync: () -> Unit,
        onNavigateBack: () -> Unit,
        onChangeRepository: () -> Unit,
        aiSection: @Composable () -> Unit,
    ) {
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        SettingsScreen(
            url = state.url,
            syncing = syncing,
            syncStatusText = syncStatusText,
            onSync = onSync,
            onNavigateBack = onNavigateBack,
            onChangeRepository = onChangeRepository,
            aiSection = aiSection,
        )
    }

    @Composable
    override fun ChangeRepoScreen(
        onPickFromGitHub: () -> Unit,
        onConnectManually: () -> Unit,
        onNavigateBack: () -> Unit,
    ) {
        val vm: SettingsViewModel = koinViewModel()
        // «Локально» переключает стор и возвращает на настройки; GitHub/вручную сбрасывают стек навигации.
        ChangeRepoScreen(
            onPickFromGitHub = onPickFromGitHub,
            onConnectManually = onConnectManually,
            onUseLocal = { vm.useLocal(); onNavigateBack() },
            onNavigateBack = onNavigateBack,
        )
    }
}
