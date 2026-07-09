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

    /** Папки + .md-файлы одного каталога: папки первыми, затем по имени (без регистра). */
    fun listEntries(dir: String): List<VaultEntry> {
        val d = dir.toPath()
        if (!fs.exists(d)) return emptyList()
        return fs.list(d)
            .filter { !it.name.startsWith(".") } // прячем .git/.obsidian/.trash и прочие dot-каталоги
            .mapNotNull { p ->
                val md = fs.metadata(p)
                when {
                    md.isDirectory -> VaultEntry(p.name, p.toString(), isFolder = true)
                    md.isRegularFile && p.name.endsWith(".md") ->
                        VaultEntry(p.name, p.toString(), isFolder = false)
                    else -> null
                }
            }
            .sortedWith(compareByDescending<VaultEntry> { it.isFolder }.thenBy { it.name.lowercase() })
    }

    /** Все файлы vault рекурсивно (dot-каталоги пропускаются), для резолвинга wikilinks. */
    fun allFiles(): List<VaultFile> {
        if (!fs.exists(root)) return emptyList()
        val out = mutableListOf<VaultFile>()
        fun walk(dir: Path) {
            for (p in fs.list(dir)) {
                if (p.name.startsWith(".")) continue
                val md = fs.metadata(p)
                when {
                    md.isDirectory -> walk(p)
                    md.isRegularFile -> out.add(
                        VaultFile(
                            relPath = p.relativeTo(root).toString().replace('\\', '/'),
                            absPath = p.toString(),
                            name = p.name,
                        ),
                    )
                }
            }
        }
        walk(root)
        return out.sortedBy { it.relPath }
    }

    fun readBytes(path: String): ByteArray = fs.read(path.toPath()) { readByteArray() }

    val rootPath: String get() = root.toString()
    fun isRoot(dir: String): Boolean = dir.toPath() == root
    fun parentOf(dir: String): String {
        val p = dir.toPath()
        return if (p == root) root.toString() else (p.parent ?: root).toString()
    }

    fun readFile(path: String): String =
        fs.read(path.toPath()) { readUtf8() }

    fun writeFile(path: String, content: String) {
        fs.write(path.toPath()) { writeUtf8(content) }
    }

    fun pathFor(name: String): String = (root / name).toString()

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
