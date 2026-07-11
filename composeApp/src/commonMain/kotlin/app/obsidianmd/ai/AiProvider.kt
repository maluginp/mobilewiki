package app.obsidianmd.ai

/**
 * Поддерживаемые AI-провайдеры. Все — OpenAI-совместимые (тот же /chat/completions и /models),
 * поэтому клиент один, различаются только базовые URL и формат ключа.
 * defaultModel — модель по умолчанию (пусто = провайдер требует явного выбора в пикере).
 * supportsModelFilters — отдаёт ли /models метаданные (цена/контекст), т.е. имеет ли смысл
 * показывать фильтр/сортировку в пикере моделей (OpenRouter — да, provod.ai — нет).
 */
enum class AiProvider(
    val id: String,
    val label: String,
    val chatUrl: String,
    val modelsUrl: String,
    val keyExample: String,
    val defaultModel: String,
    val supportsModelFilters: Boolean,
) {
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        chatUrl = "https://openrouter.ai/api/v1/chat/completions",
        modelsUrl = "https://openrouter.ai/api/v1/models",
        keyExample = "sk-or-...",
        defaultModel = DEFAULT_MODEL,
        supportsModelFilters = true,
    ),
    PROVOD(
        id = "provod",
        label = "provod.ai",
        chatUrl = "https://api.provod.ai/v1/chat/completions",
        modelsUrl = "https://api.provod.ai/v1/models",
        keyExample = "sk_...",
        defaultModel = "",
        supportsModelFilters = false,
    ),

    // Любой OpenAI-совместимый эндпоинт: URL задаёт пользователь (base URL вида
    // https://host/v1), из него достраиваются /chat/completions и /models.
    CUSTOM(
        id = "custom",
        label = "Custom",
        chatUrl = "",
        modelsUrl = "",
        keyExample = "sk-...",
        defaultModel = "",
        supportsModelFilters = false,
    );

    val needsBaseUrl: Boolean get() = this == CUSTOM

    /** URL /chat/completions: у CUSTOM собирается из base URL, у остальных — зашитый. */
    fun resolvedChatUrl(customBaseUrl: String): String =
        if (this == CUSTOM) joinUrl(customBaseUrl, "chat/completions") else chatUrl

    /** URL /models: у CUSTOM собирается из base URL, у остальных — зашитый. */
    fun resolvedModelsUrl(customBaseUrl: String): String =
        if (this == CUSTOM) joinUrl(customBaseUrl, "models") else modelsUrl

    companion object {
        val DEFAULT = OPENROUTER
        fun byId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

// base "https://host/v1" (+/- слэш) + "chat/completions" → "https://host/v1/chat/completions".
// Пустой base → пусто (запрос уйдёт в никуда и вернёт понятную ошибку — пусть юзер задаст URL).
private fun joinUrl(base: String, path: String): String {
    val b = base.trim().trimEnd('/')
    return if (b.isEmpty()) "" else "$b/$path"
}
