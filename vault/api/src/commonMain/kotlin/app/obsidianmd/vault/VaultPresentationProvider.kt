package app.obsidianmd.vault

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable

/**
 * Точка входа UI фичи vault для навигации основного модуля. Реализация — в :vault:impl
 * (internal), подключается через DI. Основной модуль не знает о конкретных экранах фичи.
 */
@OptIn(ExperimentalMaterial3Api::class)
interface VaultPresentationProvider {
    /** Экран списка файлов/папок vault. */
    @Composable
    fun ListScreen(
        entries: List<VaultEntry>,
        loading: Boolean,
        refreshing: Boolean,
        query: String,
        results: List<MdFile>,
        onOpenFile: (MdFile) -> Unit,
        onOpenFolder: (VaultEntry) -> Unit,
        onRefresh: () -> Unit,
        scrollBehavior: TopAppBarScrollBehavior,
    )
}
