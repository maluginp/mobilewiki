package app.obsidianmd.ai

/**
 * Поддерживаемые AI-провайдеры. Все — OpenAI-совместимые (тот же /chat/completions и /models),
 * поэтому клиент один, различаются только базовые URL и формат ключа.
 * defaultModel — модель по умолчанию (пусто = провайдер требует явного выбора в пикере).
 */
enum class AiProvider(
    val id: String,
    val label: String,
    val chatUrl: String,
    val modelsUrl: String,
    val keyExample: String,
    val defaultModel: String,
) {
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        chatUrl = "https://openrouter.ai/api/v1/chat/completions",
        modelsUrl = "https://openrouter.ai/api/v1/models",
        keyExample = "sk-or-...",
        defaultModel = DEFAULT_MODEL,
    ),
    PROVOD(
        id = "provod",
        label = "provod.ai",
        chatUrl = "https://api.provod.ai/v1/chat/completions",
        modelsUrl = "https://api.provod.ai/v1/models",
        keyExample = "sk_...",
        defaultModel = "",
    );

    companion object {
        val DEFAULT = OPENROUTER
        fun byId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
