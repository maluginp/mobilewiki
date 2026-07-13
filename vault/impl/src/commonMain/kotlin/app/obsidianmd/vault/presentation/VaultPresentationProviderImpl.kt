package app.obsidianmd.vault.presentation

import androidx.compose.runtime.Composable
import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultEntry
import app.obsidianmd.vault.VaultPresentationProvider

internal class VaultPresentationProviderImpl : VaultPresentationProvider {
    @Composable
    override fun ListScreen(
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
        bottomBar: @Composable () -> Unit,
    ) = VaultListScreen(
        title = title,
        entries = entries,
        loading = loading,
        refreshing = refreshing,
        query = query,
        results = results,
        onQueryChange = onQueryChange,
        onOpenFile = onOpenFile,
        onOpenFolder = onOpenFolder,
        onRefresh = onRefresh,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        bottomBar = bottomBar,
    )
}
