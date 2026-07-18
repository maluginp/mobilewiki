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
        onChangeRepository: () -> Unit,
        aiSection: @Composable () -> Unit,
    )

    /** Отдельный экран смены репозитория: предупреждение + выбор типа подключения. */
    @Composable
    fun ChangeRepoScreen(
        onPickFromGitHub: () -> Unit,
        onConnectManually: () -> Unit,
        onNavigateBack: () -> Unit,
    )
}
