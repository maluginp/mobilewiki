package app.obsidianmd.vault

/** Файл vault для резолвинга ссылок: путь от корня, абсолютный путь, имя с расширением. */
data class VaultFile(val relPath: String, val absPath: String, val name: String)

/** Блок отрисованной заметки: текст (markdown) или встроенная картинка. */
sealed interface MdBlock {
    data class Text(val markdown: String) : MdBlock
    data class Image(val absPath: String) : MdBlock
}

/** Заметка, разобранная на блоки; linkTargets[idx] — absPath для ссылки `wikilink:idx`. */
data class RenderedNote(val blocks: List<MdBlock>, val linkTargets: List<String>)

private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")
private val WIKILINK = Regex("""(!?)\[\[([^\]\n]+)]]""")

/**
 * Ищет файл vault по цели wikilink. Последний сегмент с расширением берётся как есть,
 * иначе добавляется `.md`. С `/` — точный матч по relPath; без — по имени в любой папке
 * (приоритет меньшей глубине, тай-брейк по relPath). Не найдено → null.
 */
fun resolveWikiLink(target: String, files: List<VaultFile>): VaultFile? {
    val t = target.trim().substringBefore('|').substringBefore('#').trim()
    if (t.isEmpty()) return null
    val last = t.substringAfterLast('/')
    val fname = if (last.contains('.')) last else "$last.md"
    return if (t.contains('/')) {
        val dir = t.substringBeforeLast('/').trim('/')
        val wanted = if (dir.isEmpty()) fname else "$dir/$fname"
        files.firstOrNull { it.relPath == wanted }
    } else {
        files.filter { it.name == fname }
            .minWithOrNull(compareBy({ it.relPath.count { c -> c == '/' } }, { it.relPath }))
    }
}

private fun isImage(target: String): Boolean =
    target.substringAfterLast('.', "").lowercase() in IMAGE_EXTS

/**
 * Разбирает контент: `![[img.ext]]` (картинка, найдено, на отдельной строке) → блок Image;
 * `[[...]]` (найдено) → markdown-ссылка `[label](wikilink:idx)`; всё ненайденное — как текст.
 */
fun renderNote(content: String, files: List<VaultFile>): RenderedNote {
    val targets = mutableListOf<String>()
    val blocks = mutableListOf<MdBlock>()
    val textBuf = StringBuilder()

    fun flush() {
        if (textBuf.isNotEmpty()) {
            blocks.add(MdBlock.Text(textBuf.toString()))
            textBuf.clear()
        }
    }

    for (line in content.split("\n")) {
        val whole = WIKILINK.matchEntire(line.trim())
        if (whole != null && whole.groupValues[1] == "!") {
            val target = whole.groupValues[2]
            val resolved = resolveWikiLink(target, files)
            if (resolved != null && isImage(target)) {
                flush()
                blocks.add(MdBlock.Image(resolved.absPath))
                continue
            }
        }
        val rewritten = WIKILINK.replace(line) { m ->
            val isEmbed = m.groupValues[1] == "!"
            val target = m.groupValues[2]
            val resolved = resolveWikiLink(target, files)
            if (!isEmbed && resolved != null) {
                val idx = targets.size
                targets.add(resolved.absPath)
                "[$target](wikilink:$idx)"
            } else {
                m.value // эмбед-картинка вне отдельной строки или ненайденное — оставляем как есть
            }
        }
        if (textBuf.isNotEmpty()) textBuf.append("\n")
        textBuf.append(rewritten)
    }
    flush()
    return RenderedNote(blocks, targets)
}
