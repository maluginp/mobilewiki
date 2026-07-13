package app.obsidianmd.vault.data

import android.content.Context

/** Каталог vault в приватном хранилище приложения (создаётся при отсутствии). */
fun vaultRootPath(context: Context): String {
    val root = context.filesDir.resolve("vault")
    if (!root.exists()) root.mkdirs()
    return root.absolutePath
}
