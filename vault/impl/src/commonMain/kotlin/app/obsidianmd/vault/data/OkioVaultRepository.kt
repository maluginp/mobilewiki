package app.obsidianmd.vault.data

import app.obsidianmd.vault.DocRef
import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.SkillMeta
import app.obsidianmd.vault.VaultEntry
import app.obsidianmd.vault.VaultFile
import app.obsidianmd.vault.VaultRepository
import app.obsidianmd.vault.domain.firstHeading
import app.obsidianmd.vault.domain.frontmatter
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Реализация [VaultRepository] поверх okio-файловой системы.
 * Создаётся только в DI-модуле фичи (см. `di/vaultPlatformModule`); в проде — с [FileSystem.SYSTEM].
 */
class OkioVaultRepository(
    private val fs: FileSystem,
    private val root: Path,
) : VaultRepository {
    override fun listMarkdownFiles(): List<MdFile> {
        if (!fs.exists(root)) return emptyList()
        return fs.list(root)
            .filter { fs.metadata(it).isRegularFile && it.name.endsWith(".md") }
            .sortedBy { it.name }
            .map { MdFile(name = it.name, path = it.toString()) }
    }

    /** Папки + .md-файлы одного каталога: папки первыми, затем по имени (без регистра). */
    override fun listEntries(dir: String): List<VaultEntry> {
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
    override fun allFiles(): List<VaultFile> {
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

    // Список .md-документов с заголовком и целью wikilink — для пикера вставки ссылок.
    // ponytail: читает первую строку-заголовок каждого файла; ок для личного vault.
    override fun documents(): List<DocRef> = allFiles()
        .filter { it.name.endsWith(".md") }
        .map {
            val base = it.name.removeSuffix(".md")
            DocRef(relPath = it.relPath, title = firstHeading(readFile(it.absPath)) ?: base, target = base)
        }

    // --- Skills: инструкции для AI-агента из .claude/skills/<slug>/SKILL.md и .codex/skills/... ---
    // Формат совместим с Claude Code: frontmatter name/description + тело.

    /** Скиллы, найденные в vault. body грузится лениво (только name/description для system prompt). */
    override fun listSkills(): List<SkillMeta> {
        val out = mutableListOf<SkillMeta>()
        for (base in listOf(".claude", ".codex")) {
            val skillsDir = root / base / "skills"
            if (!fs.exists(skillsDir)) continue
            for (dir in fs.list(skillsDir)) {
                if (!fs.metadata(dir).isDirectory) continue
                val file = dir / "SKILL.md"
                if (!fs.exists(file)) continue
                val fm = frontmatter(fs.read(file) { readUtf8() })
                out.add(
                    SkillMeta(
                        name = fm["name"]?.ifBlank { null } ?: dir.name,
                        description = fm["description"].orEmpty(),
                        path = file.toString(),
                    ),
                )
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }

    /** Полный текст SKILL.md по имени скилла; null, если такого нет. */
    override fun readSkill(name: String): String? =
        listSkills().firstOrNull { it.name == name }?.let { readFile(it.path) }

    override fun readBytes(path: String): ByteArray = fs.read(path.toPath()) { readByteArray() }

    override val rootPath: String get() = root.toString()
    override fun isRoot(dir: String): Boolean = dir.toPath() == root
    override fun parentOf(dir: String): String {
        val p = dir.toPath()
        return if (p == root) root.toString() else (p.parent ?: root).toString()
    }

    override fun readFile(path: String): String =
        fs.read(path.toPath()) { readUtf8() }

    override fun writeFile(path: String, content: String) {
        fs.write(path.toPath()) { writeUtf8(content) }
    }

    override fun pathFor(name: String): String = (root / name).toString()

    // ponytail: наивный полный скан содержимого, без индекса — ок для личного vault;
    // индекс, если станет медленно на больших хранилищах.
    // Ищем по всему дереву (включая подпапки), не только по корню.
    override fun search(query: String): List<MdFile> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        return allFiles()
            .filter { it.name.endsWith(".md") }
            .filter { it.name.lowercase().contains(q) || readFile(it.absPath).lowercase().contains(q) }
            .map { MdFile(name = it.name, path = it.absPath) }
    }
}
