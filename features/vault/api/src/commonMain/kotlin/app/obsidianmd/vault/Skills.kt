package app.obsidianmd.vault

/** Скилл AI-агента: имя, описание (для system prompt) и путь к SKILL.md (тело — по требованию). */
data class SkillMeta(val name: String, val description: String, val path: String)
