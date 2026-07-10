package app.obsidianmd.vault

/** Документ для пикера ссылок: путь от корня, заголовок (или имя), цель wikilink (базовое имя). */
data class DocRef(val relPath: String, val title: String, val target: String)

private val HEADING = Regex("""^#{1,6}\s+(.+)$""")

/** Первый markdown-заголовок (любого уровня) в контенте, без решёток; null если нет. */
fun firstHeading(content: String): String? {
    for (line in content.split("\n")) {
        val m = HEADING.matchEntire(line.trim()) ?: continue
        return m.groupValues[1].trim()
    }
    return null
}
