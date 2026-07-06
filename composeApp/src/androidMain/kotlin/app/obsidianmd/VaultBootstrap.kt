package app.obsidianmd

import android.content.Context
import app.obsidianmd.vault.VaultRepository
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

fun vaultRoot(context: Context): Path =
    context.filesDir.resolve("vault").absolutePath.toPath()

fun ensureSampleVault(root: Path, fs: FileSystem = FileSystem.SYSTEM) {
    if (!fs.exists(root)) fs.createDirectories(root)
    val welcome = root / "welcome.md"
    if (!fs.exists(welcome)) {
        fs.write(welcome) {
            writeUtf8(
                """
                # Welcome

                Это **демо**-заметка с [ссылкой](https://obsidian.md) и `кодом`.

                - пункт 1
                - пункт 2
                """.trimIndent(),
            )
        }
    }
    val notes = root / "notes.md"
    if (!fs.exists(notes)) {
        fs.write(notes) { writeUtf8("# Notes\n\nВторой файл.") }
    }
}

fun createRepository(context: Context): VaultRepository {
    val root = vaultRoot(context)
    ensureSampleVault(root)
    return VaultRepository(FileSystem.SYSTEM, root)
}
