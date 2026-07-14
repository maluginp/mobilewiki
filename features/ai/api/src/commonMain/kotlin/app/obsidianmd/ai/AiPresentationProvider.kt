package app.obsidianmd.ai

import androidx.compose.runtime.Composable

/**
 * Точка входа UI фичи AI для навигации основного модуля. Реализация — в :ai:impl (internal),
 * подключается через DI.
 */
interface AiPresentationProvider {
    @Composable fun aiEnabled(): Boolean
    @Composable fun Chat(
        onOpenFile: (path: String) -> Unit,
        onOpenSettings: () -> Unit,
    )
    @Composable fun ModelPicker(onNavigateBack: () -> Unit)
    @Composable fun SettingsSection(onEditModel: () -> Unit)
}
