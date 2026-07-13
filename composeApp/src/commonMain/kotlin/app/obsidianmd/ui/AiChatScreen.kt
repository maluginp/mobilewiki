package app.obsidianmd.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.obsidianmd.ai.AiStatus
import app.obsidianmd.ai.ChatTurn
import app.obsidianmd.ui.theme.Spacing
import app.obsidianmd.vault.MdBlock
import app.obsidianmd.vault.VaultFile
import app.obsidianmd.vault.renderNote
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_apply
import app.obsidianmd.resources.action_reject
import app.obsidianmd.resources.ai_write_title
import app.obsidianmd.resources.cd_ai_avatar
import app.obsidianmd.resources.cd_send
import app.obsidianmd.resources.chat_empty_subtitle
import app.obsidianmd.resources.chat_empty_title
import app.obsidianmd.resources.chat_input_hint
import app.obsidianmd.resources.error_with_reason
import app.obsidianmd.resources.title_ai_chat
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.jetbrains.compose.resources.stringResource

private val BUBBLE_RADIUS = 18.dp        // = MaterialTheme.shapes.large
private val BUBBLE_MAX_WIDTH = 300.dp     // максимальная ширина пузыря сообщения

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    messages: List<ChatTurn>,
    status: AiStatus,
    pendingWrite: Pair<String, String>?,
    onSend: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    files: List<VaultFile> = emptyList(),
    onOpenFile: (String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    val thinking = status is AiStatus.Thinking
    val listState = rememberLazyListState()

    // Держим последнее сообщение / индикатор в поле зрения.
    val lastIndex = messages.size + if (thinking) 0 else -1
    androidx.compose.runtime.LaunchedEffect(messages.size, thinking) {
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.title_ai_chat)) }) },
        bottomBar = bottomBar,
    ) { scaffoldPadding ->
    // Поднимаем поле ввода над клавиатурой (а не уводим весь экран с AppBar вверх).
    // Вычитаем navigationBars: Scaffold уже добавил их в content padding — иначе двойной отступ.
    Column(
        Modifier.fillMaxSize().padding(scaffoldPadding)
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        if (messages.isEmpty() && !thinking && status !is AiStatus.Failed) {
            EmptyState(Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.sm),
            ) {
                items(messages.size) { i ->
                    MessageBubble(messages[i], files, onOpenFile)
                }
                if (thinking) item { TypingIndicator() }
                if (status is AiStatus.Failed) item { ErrorBubble(status.reason) }
            }
        }
        ChatInputBar(
            value = input,
            onValueChange = { input = it },
            sendEnabled = input.isNotBlank() && !thinking,
            onSend = { onSend(input); input = "" },
        )
    }
    }

    if (pendingWrite != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.ai_write_title, pendingWrite.first)) },
            text = { Text(pendingWrite.second.take(500)) },
            confirmButton = { TextButton(onClick = onApprove) { Text(stringResource(Res.string.action_apply)) } },
            dismissButton = { TextButton(onClick = onReject) { Text(stringResource(Res.string.action_reject)) } },
        )
    }
}

@Composable
private fun MessageBubble(
    turn: ChatTurn,
    files: List<VaultFile>,
    onOpenFile: (String) -> Unit,
) {
    val isUser = turn.role == "user"
    val bubbleColor =
        if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor =
        if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    // Асимметричный «хвостик» пузыря: большой радиус (shapes.large = 18dp) со всех сторон,
    // кроме нижнего угла со стороны отправителя.
    val r = BUBBLE_RADIUS
    val shape = RoundedCornerShape(
        topStart = r,
        topEnd = r,
        bottomStart = if (isUser) r else Spacing.xs,
        bottomEnd = if (isUser) Spacing.xs else r,
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AvatarBadge()
            Spacer(Modifier.width(Spacing.sm))
        }
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = BUBBLE_MAX_WIDTH),
        ) {
            val pad = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            if (isUser) {
                // Пользователь markdown не пишет — обычный текст (и белым по primary).
                Text(turn.text, color = textColor, modifier = pad)
            } else {
                // renderNote переписывает [[wikilinks]] на файлы vault в кликабельные ссылки.
                val note = remember(turn.text, files) { renderNote(turn.text, files) }
                val handler = remember(note.linkTargets, onOpenFile) {
                    object : UriHandler {
                        override fun openUri(uri: String) {
                            if (uri.startsWith("wikilink:")) {
                                uri.removePrefix("wikilink:").toIntOrNull()
                                    ?.let { note.linkTargets.getOrNull(it) }
                                    ?.let(onOpenFile)
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalUriHandler provides handler) {
                    Column(pad) {
                        note.blocks.forEach { block ->
                            if (block is MdBlock.Text) {
                                Markdown(
                                    content = block.markdown,
                                    colors = markdownColor(text = textColor),
                                    typography = chatMarkdownTypography(),
                                    // Дефолтный модификатор Markdown — fillMaxSize(); в LazyColumn с
                                    // бесконечной высотой это вешает layout. Ограничиваем шириной.
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Компактная типографика для чата: заголовки без «гигантизма» — это мобилка.
@Composable
private fun chatMarkdownTypography() = MaterialTheme.typography.let { t ->
    markdownTypography(
        h1 = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        h2 = t.titleSmall.copy(fontWeight = FontWeight.Bold),
        h3 = t.bodyLarge.copy(fontWeight = FontWeight.Bold),
        h4 = t.bodyMedium.copy(fontWeight = FontWeight.Bold),
        h5 = t.bodyMedium.copy(fontWeight = FontWeight.Bold),
        h6 = t.bodySmall.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun AvatarBadge(size: Int = 32) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = stringResource(Res.string.cd_ai_avatar),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size((size * 0.58f).dp),
        )
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs).testTag("typing"),
        verticalAlignment = Alignment.Bottom,
    ) {
        AvatarBadge()
        Spacer(Modifier.width(Spacing.sm))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(BUBBLE_RADIUS, BUBBLE_RADIUS, BUBBLE_RADIUS, Spacing.xs),
        ) {
            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                val transition = rememberInfiniteTransition(label = "typing")
                repeat(3) { i ->
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = i * 180),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "dot$i",
                    )
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(7.dp).alpha(alpha)
                            .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBubble(reason: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Text(
            stringResource(Res.string.error_with_reason, reason),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AvatarBadge(size = 72)
        Spacer(Modifier.size(Spacing.lg))
        Text(
            stringResource(Res.string.chat_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            stringResource(Res.string.chat_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(Res.string.chat_input_hint)) },
                maxLines = 4,
                shape = RectangleShape,
                modifier = Modifier.weight(1f).testTag("chat_input"),
            )
            Spacer(Modifier.width(Spacing.sm))
            val sendBg =
                if (sendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(sendBg),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onSend, enabled = sendEnabled) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(Res.string.cd_send),
                        tint = if (sendEnabled) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
