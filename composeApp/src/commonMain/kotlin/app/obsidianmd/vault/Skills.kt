package app.obsidianmd.vault

/** Скилл AI-агента: имя, описание (для system prompt) и путь к SKILL.md (тело — по требованию). */
data class SkillMeta(val name: String, val description: String, val path: String)

/**
 * Минимальный парсер YAML-frontmatter: ведущий блок между `---` строками → плоская map key→value.
 * Только то, что нужно скиллам (name/description); без вложенности, списков и кавычек-эскейпов.
 */
fun frontmatter(text: String): Map<String, String> {
    val lines = text.split("\n")
    if (lines.firstOrNull()?.trim() != "---") return emptyMap()
    val out = mutableMapOf<String, String>()
    for (line in lines.drop(1)) {
        if (line.trim() == "---") break
        val i = line.indexOf(':')
        if (i <= 0) continue
        out[line.substring(0, i).trim()] = line.substring(i + 1).trim().trim('"', '\'')
    }
    return out
}
