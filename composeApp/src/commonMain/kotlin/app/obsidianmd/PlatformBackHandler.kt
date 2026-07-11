package app.obsidianmd

import androidx.compose.runtime.Composable

/** Перехват системной кнопки «назад». enabled=false — событие уходит системе (сворачивание). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
