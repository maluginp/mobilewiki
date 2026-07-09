package app.obsidianmd.ui

import androidx.compose.ui.graphics.ImageBitmap

/** Декодирует байты картинки в ImageBitmap (или null, если не вышло). */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?
