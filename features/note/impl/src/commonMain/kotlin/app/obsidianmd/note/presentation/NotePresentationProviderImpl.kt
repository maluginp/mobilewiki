package app.obsidianmd.note.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.obsidianmd.note.NotePresentationProvider
import app.obsidianmd.vault.DocRef
import app.obsidianmd.vault.VaultFile

internal class NotePresentationProviderImpl : NotePresentationProvider {
    @Composable
    override fun NoteScreen(
        title: String,
        content: String,
        files: List<VaultFile>,
        documents: List<DocRef>,
        loadImage: (String) -> ImageBitmap?,
        onOpenPath: (String) -> Unit,
        onNavigateBack: () -> Unit,
        onSave: (String) -> Unit,
    ) {
        NoteScreenContent(
            title = title,
            content = content,
            files = files,
            documents = documents,
            loadImage = loadImage,
            onOpenPath = onOpenPath,
            onNavigateBack = onNavigateBack,
            onSave = onSave,
        )
    }
}
