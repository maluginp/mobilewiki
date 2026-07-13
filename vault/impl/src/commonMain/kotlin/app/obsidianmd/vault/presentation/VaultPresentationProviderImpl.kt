package app.obsidianmd.vault.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultEntry
import app.obsidianmd.vault.VaultPresentationProvider

@OptIn(ExperimentalMaterial3Api::class)
internal class VaultPresentationProviderImpl : VaultPresentationProvider {
    @Composable
    override fun ListScreen(
        entries: List<VaultEntry>,
        loading: Boolean,
        refreshing: Boolean,
        query: String,
        results: List<MdFile>,
        onOpenFile: (MdFile) -> Unit,
        onOpenFolder: (VaultEntry) -> Unit,
        onRefresh: () -> Unit,
        scrollBehavior: TopAppBarScrollBehavior,
    ) = VaultListScreen(
        entries = entries,
        loading = loading,
        refreshing = refreshing,
        query = query,
        results = results,
        onOpenFile = onOpenFile,
        onOpenFolder = onOpenFolder,
        onRefresh = onRefresh,
        scrollBehavior = scrollBehavior,
    )
}
