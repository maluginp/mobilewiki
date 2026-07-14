package app.obsidianmd.settings

import androidx.compose.runtime.Composable

/** Точка входа UI настроек для навигации основного модуля. Реализация — в :settings:impl. */
interface SettingsPresentationProvider {
    @Composable
    fun Screen(
        syncing: Boolean,
        syncStatusText: String,
        onSync: () -> Unit,
        onNavigateBack: () -> Unit,
        onPickFromGitHub: () -> Unit,
        aiSection: @Composable () -> Unit,
    )
}
