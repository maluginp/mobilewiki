package app.obsidianmd.note

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.obsidianmd.vault.DocRef
import app.obsidianmd.vault.VaultFile

/** Точка входа UI заметки для навигации основного модуля. Реализация — в :note:impl. */
interface NotePresentationProvider {
    @Composable
    fun NoteScreen(
        title: String,
        content: String,
        files: List<VaultFile>,
        documents: List<DocRef>,
        loadImage: (String) -> ImageBitmap?,
        onOpenPath: (String) -> Unit,
        onNavigateBack: () -> Unit,
        onSave: (String) -> Unit,
        bottomBar: @Composable () -> Unit,
    )
}
