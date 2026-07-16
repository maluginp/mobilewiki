package app.obsidianmd.vault

/**
 * In-memory [VaultRepository] для тестов composeApp (реальная реализация — internal в :vault:impl).
 * Ключи — абсолютные пути; папки выводятся из путей. Достаточно для тестов VM и AI-агента.
 */
class FakeVaultRepository(
    override val rootPath: String = "/vault",
    seed: Map<String, String> = emptyMap(),
) : VaultRepository {
    private val files = LinkedHashMap<String, String>(seed)

    private fun rel(path: String) = path.removePrefix("$rootPath/")

    override fun allFiles(): List<VaultFile> =
        files.keys
            .filterNot { rel(it).split('/').any { seg -> seg.startsWith(".") } }
            .map { VaultFile(relPath = rel(it), absPath = it, name = it.substringAfterLast('/')) }
            .sortedBy { it.relPath }

    override fun listEntries(dir: String): List<VaultEntry> {
        val prefix = if (dir.endsWith("/")) dir else "$dir/"
        val folders = LinkedHashSet<String>()
        val filesHere = mutableListOf<VaultEntry>()
        for (path in files.keys) {
            if (!path.startsWith(prefix)) continue
            val rest = path.removePrefix(prefix)
            if (rest.startsWith(".")) continue
            if (rest.contains('/')) {
                folders.add(rest.substringBefore('/'))
            } else if (rest.endsWith(".md")) {
                filesHere.add(VaultEntry(rest, path, isFolder = false))
            }
        }
        val folderEntries = folders.map { VaultEntry(it, "$prefix$it", isFolder = true) }
        return (folderEntries + filesHere)
            .sortedWith(compareByDescending<VaultEntry> { it.isFolder }.thenBy { it.name.lowercase() })
    }

    override fun listMarkdownFiles(): List<MdFile> =
        listEntries(rootPath).filter { !it.isFolder }.map { MdFile(it.name, it.path) }

    override fun search(query: String): List<MdFile> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        return allFiles()
            .filter { it.name.endsWith(".md") }
            .filter { it.name.lowercase().contains(q) || readFile(it.absPath).lowercase().contains(q) }
            .map { MdFile(it.name, it.absPath) }
    }

    override fun documents(): List<DocRef> = allFiles()
        .filter { it.name.endsWith(".md") }
        .map { DocRef(relPath = it.relPath, title = it.name.removeSuffix(".md"), target = it.name.removeSuffix(".md")) }

    override fun listSkills(): List<SkillMeta> = emptyList()
    override fun readSkill(name: String): String? = null

    override fun readBytes(path: String): ByteArray = readFile(path).encodeToByteArray()
    override fun readFile(path: String): String = files[path] ?: ""
    override fun writeFile(path: String, content: String) { files[path] = content }
    override fun pathFor(name: String): String = "$rootPath/$name"

    // Папку моделируем маркерным ключом внутри неё — listEntries выводит папки из путей;
    // ключ на "." отфильтровывается из содержимого, так что папка видна, но пустая.
    override fun createFolder(path: String) { files["$path/.keep"] = "" }

    override fun isRoot(dir: String): Boolean = dir == rootPath
    override fun parentOf(dir: String): String =
        if (dir == rootPath) rootPath else dir.substringBeforeLast('/').ifEmpty { rootPath }
}
