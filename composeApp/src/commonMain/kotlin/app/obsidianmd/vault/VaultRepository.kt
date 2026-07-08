package app.obsidianmd.vault

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class VaultRepository(
    private val fs: FileSystem,
    private val root: Path,
) {
    fun listMarkdownFiles(): List<MdFile> {
        if (!fs.exists(root)) return emptyList()
        return fs.list(root)
            .filter { fs.metadata(it).isRegularFile && it.name.endsWith(".md") }
            .sortedBy { it.name }
            .map { MdFile(name = it.name, path = it.toString()) }
    }

    fun readFile(path: String): String =
        fs.read(path.toPath()) { readUtf8() }

    fun writeFile(path: String, content: String) {
        fs.write(path.toPath()) { writeUtf8(content) }
    }

    // ponytail: наивный полный скан содержимого, без индекса — ок для личного vault;
    // индекс, если станет медленно на больших хранилищах.
    fun search(query: String): List<MdFile> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        return listMarkdownFiles().filter { f ->
            f.name.lowercase().contains(q) || readFile(f.path).lowercase().contains(q)
        }
    }
}
