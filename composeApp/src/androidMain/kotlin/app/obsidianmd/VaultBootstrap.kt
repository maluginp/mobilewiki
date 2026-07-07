package app.obsidianmd

import android.content.Context
import app.obsidianmd.vault.VaultRepository
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

fun vaultRoot(context: Context): Path =
    context.filesDir.resolve("vault").absolutePath.toPath()

fun createRepository(context: Context): VaultRepository {
    val root = vaultRoot(context)
    if (!FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.createDirectories(root)
    return VaultRepository(FileSystem.SYSTEM, root)
}
