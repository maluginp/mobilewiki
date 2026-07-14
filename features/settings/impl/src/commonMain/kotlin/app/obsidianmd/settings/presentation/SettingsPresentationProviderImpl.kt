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
        onPickFromGitHub: () -> Unit,
        aiSection: @Composable () -> Unit,
    ) {
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        SettingsScreen(
            url = state.url,
            onSave = vm::save,
            syncing = syncing,
            syncStatusText = syncStatusText,
            onSync = onSync,
            onNavigateBack = onNavigateBack,
            onPickFromGitHub = onPickFromGitHub,
            aiSection = aiSection,
        )
    }
}
