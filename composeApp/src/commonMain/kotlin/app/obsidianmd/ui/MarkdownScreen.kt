package app.obsidianmd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import app.obsidianmd.vault.MdBlock
import app.obsidianmd.vault.VaultFile
import app.obsidianmd.vault.renderNote
import com.mikepenz.markdown.m3.Markdown

// Правкой управляет AppBar (иконки Edit/Save); экран показывает просмотр или редактор.
@Composable
fun MarkdownScreen(
    content: String,
    editing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    files: List<VaultFile>,
    loadImage: (String) -> ImageBitmap?,
    onOpenPath: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            )
        } else {
            val note = remember(content, files) { renderNote(content, files) }
            val platform = LocalUriHandler.current
            val handler = remember(note.linkTargets, platform) {
                object : UriHandler {
                    override fun openUri(uri: String) {
                        if (uri.startsWith("wikilink:")) {
                            uri.removePrefix("wikilink:").toIntOrNull()
                                ?.let { note.linkTargets.getOrNull(it) }
                                ?.let(onOpenPath)
                        } else {
                            platform.openUri(uri)
                        }
                    }
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalUriHandler provides handler) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    for (block in note.blocks) {
                        when (block) {
                            is MdBlock.Text -> Markdown(block.markdown)
                            is MdBlock.Image -> {
                                // ponytail: чтение файла на композиции — ок для мелких картинок личного vault.
                                val bmp = remember(block.absPath) { loadImage(block.absPath) }
                                if (bmp != null) {
                                    Image(
                                        bmp,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.FillWidth,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
