package app.obsidianmd.vault

import androidx.compose.runtime.Composable

/**
 * Точка входа UI фичи vault для навигации основного модуля. Реализация — в :vault:impl
 * (internal), подключается через DI. Основной модуль не знает о конкретных экранах фичи.
 */
interface VaultPresentationProvider {
    /**
     * Экран списка файлов/папок vault со своим TopAppBar (заголовок, поиск, действие «настройки»).
     * @param title заголовок (имя папки или «Заметки» для корня)
     * @param onBack null на корне; стрелка «назад» для вложенной папки
     */
    @Composable
    fun ListScreen(
        title: String,
        entries: List<VaultEntry>,
        loading: Boolean,
        refreshing: Boolean,
        query: String,
        results: List<MdFile>,
        onQueryChange: (String) -> Unit,
        onOpenFile: (MdFile) -> Unit,
        onOpenFolder: (VaultEntry) -> Unit,
        onRefresh: () -> Unit,
        onOpenSettings: () -> Unit,
        onBack: (() -> Unit)?,
    )
}
