package app.obsidianmd.vault

/** Чистая логика имён для создания заметок и папок. Без IO — тестируется в изоляции. */

/** Имя файла заметки: обрезает пробелы и добавляет `.md`, если расширения ещё нет (без регистра). */
fun noteFileName(raw: String): String {
    val name = raw.trim()
    return if (name.endsWith(".md", ignoreCase = true)) name else "$name.md"
}

enum class NameError { Blank, Slash, Exists }

/**
 * Ошибка имени новой записи или null, если имя годное.
 * [finalName] — уже приведённое имя (заметка → [noteFileName], папка → trim).
 * [existing] — имена записей текущего каталога.
 */
fun entryNameError(finalName: String, existing: List<String>): NameError? {
    val trimmed = finalName.trim()
    return when {
        trimmed.isEmpty() || trimmed == ".md" -> NameError.Blank
        trimmed.contains('/') -> NameError.Slash
        existing.any { it.equals(trimmed, ignoreCase = true) } -> NameError.Exists
        else -> null
    }
}
