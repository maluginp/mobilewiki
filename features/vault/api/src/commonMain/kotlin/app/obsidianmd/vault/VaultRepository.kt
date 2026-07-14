package app.obsidianmd.vault

/**
 * Контракт доступа к vault (файлы, папки, скиллы, поиск). Реализация — в :vault:impl.
 * Другие фичи зависят только от этого интерфейса, не от реализации.
 */
interface VaultRepository {
    fun listMarkdownFiles(): List<MdFile>

    /** Папки + .md-файлы одного каталога: папки первыми, затем по имени (без регистра). */
    fun listEntries(dir: String): List<VaultEntry>

    /** Все файлы vault рекурсивно (dot-каталоги пропускаются), для резолвинга wikilinks. */
    fun allFiles(): List<VaultFile>

    /** Список .md-документов с заголовком и целью wikilink — для пикера вставки ссылок. */
    fun documents(): List<DocRef>

    /** Скиллы, найденные в vault (.claude/.codex). body грузится лениво (readSkill). */
    fun listSkills(): List<SkillMeta>

    /** Полный текст SKILL.md по имени скилла; null, если такого нет. */
    fun readSkill(name: String): String?

    fun readBytes(path: String): ByteArray

    val rootPath: String
    fun isRoot(dir: String): Boolean
    fun parentOf(dir: String): String

    fun readFile(path: String): String
    fun writeFile(path: String, content: String)
    fun pathFor(name: String): String

    fun search(query: String): List<MdFile>
}
