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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.obsidianmd.editor.EditState
import app.obsidianmd.editor.MdEdit
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_cancel
import app.obsidianmd.resources.action_discard
import app.obsidianmd.resources.action_edit
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.link_note_title
import app.obsidianmd.resources.unsaved_message
import app.obsidianmd.resources.unsaved_title
import app.obsidianmd.vault.DocRef
import app.obsidianmd.vault.MdBlock
import app.obsidianmd.vault.VaultFile
import app.obsidianmd.vault.renderNote
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.jetbrains.compose.resources.stringResource

// Экран заметки со своим TopAppBar: назад (с защитой от потери правок) и переключатель Edit/Save.
// Режим правки и черновик — локальный стейт экрана.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownScreen(
    title: String,
    content: String,
    files: List<VaultFile>,
    documents: List<DocRef>,
    loadImage: (String) -> ImageBitmap?,
    onOpenPath: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onSave: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(content) }
    var showUnsaved by remember { mutableStateOf(false) }
    val dirty = editing && draft != content
    // Смена заметки (или контента после сохранения) — выходим из режима правки.
    LaunchedEffect(content) { editing = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        // «Назад»: из режима правки — выход из правки (с защитой), иначе — навигация назад.
                        if (editing) { if (dirty) showUnsaved = true else editing = false } else onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (editing) {
                        IconButton(onClick = { onSave(draft); editing = false }, enabled = dirty) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.action_save))
                        }
                    } else {
                        IconButton(onClick = { draft = content; editing = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.action_edit))
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (editing) {
                // Локальный TextFieldValue нужен, чтобы знать выделение для панели форматирования.
                // Не ключуем remember на draft — иначе поле сбрасывалось бы на каждый ввод.
                var tfv by remember { mutableStateOf(TextFieldValue(draft)) }
                var showDocPicker by remember { mutableStateOf(false) }
                val apply: (((EditState) -> EditState)) -> Unit = { transform ->
                    val r = transform(EditState(tfv.text, tfv.selection.start, tfv.selection.end))
                    tfv = TextFieldValue(r.text, TextRange(r.selStart, r.selEnd))
                    draft = r.text
                }
                BasicTextField(
                    value = tfv,
                    onValueChange = { tfv = it; draft = it.text },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                )
                EditorToolbar(
                    onTransform = apply,
                    onPickDocument = { showDocPicker = true },
                    modifier = Modifier.imePadding(),
                )
                if (showDocPicker) {
                    DocumentPickerDialog(
                        documents = documents,
                        onPick = { doc -> apply { MdEdit.wikiLink(it, doc.target) }; showDocPicker = false },
                        onDismiss = { showDocPicker = false },
                    )
                }
            } else {
                // Заголовки в рендере markdown по умолчанию гигантские (display/headline) — ужимаем.
                val t = MaterialTheme.typography
                val mdTypography = markdownTypography(
                    h1 = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    h2 = t.titleLarge.copy(fontWeight = FontWeight.Bold),
                    h3 = t.titleMedium.copy(fontWeight = FontWeight.Bold),
                    h4 = t.titleSmall.copy(fontWeight = FontWeight.Bold),
                    h5 = t.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    h6 = t.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
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
                                is MdBlock.Text -> Markdown(block.markdown, typography = mdTypography)
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

    if (showUnsaved) {
        AlertDialog(
            onDismissRequest = { showUnsaved = false },
            title = { Text(stringResource(Res.string.unsaved_title)) },
            text = { Text(stringResource(Res.string.unsaved_message)) },
            confirmButton = {
                TextButton(onClick = { onSave(draft); showUnsaved = false; editing = false }) {
                    Text(stringResource(Res.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsaved = false; editing = false }) {
                    Text(stringResource(Res.string.action_discard))
                }
            },
        )
    }
}

/** Диалог выбора документа для вставки wikilink: список «заголовок + путь», тап — вставка. */
@Composable
private fun DocumentPickerDialog(
    documents: List<DocRef>,
    onPick: (DocRef) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.link_note_title)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(documents, key = { it.relPath }) { doc ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onPick(doc) }.padding(vertical = 8.dp),
                    ) {
                        Text(doc.title, style = MaterialTheme.typography.bodyLarge)
                        if (doc.relPath != doc.title) {
                            Text(
                                doc.relPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
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
