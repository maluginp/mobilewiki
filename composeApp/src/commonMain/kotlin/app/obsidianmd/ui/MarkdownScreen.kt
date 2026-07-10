package app.obsidianmd.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.obsidianmd.editor.EditState
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
            // Локальный TextFieldValue нужен, чтобы знать выделение для панели форматирования.
            // Не ключуем remember на draft — иначе поле сбрасывалось бы на каждый ввод.
            var tfv by remember { mutableStateOf(TextFieldValue(draft)) }
            BasicTextField(
                value = tfv,
                onValueChange = { tfv = it; onDraftChange(it.text) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            )
            EditorToolbar(
                onTransform = { transform ->
                    val r = transform(EditState(tfv.text, tfv.selection.start, tfv.selection.end))
                    tfv = TextFieldValue(r.text, TextRange(r.selStart, r.selEnd))
                    onDraftChange(r.text)
                },
                modifier = Modifier.imePadding(),
            )
        } else {
            val note = remember(content, files) { renderNote(content, files) }
            var zoomed by remember { mutableStateOf<ImageBitmap?>(null) }
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
                                        modifier = Modifier.fillMaxWidth().clickable { zoomed = bmp },
                                        contentScale = ContentScale.FillWidth,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            zoomed?.let { img ->
                Dialog(
                    onDismissRequest = { zoomed = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    ZoomableImage(img, onClose = { zoomed = null })
                }
            }
        }
    }
}

/** Полноэкранный просмотр картинки: пинч-зум, панорамирование, даблтап — сброс/зум; закрыть — × или Назад. */
@Composable
private fun ZoomableImage(image: ImageBitmap, onClose: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) offset += pan else offset = androidx.compose.ui.geometry.Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            contentScale = ContentScale.Fit,
        )
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}
