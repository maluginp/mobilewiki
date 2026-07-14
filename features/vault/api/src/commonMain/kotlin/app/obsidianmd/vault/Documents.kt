package app.obsidianmd.vault

/** Документ для пикера ссылок: путь от корня, заголовок (или имя), цель wikilink (базовое имя). */
data class DocRef(val relPath: String, val title: String, val target: String)
