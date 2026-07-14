package app.obsidianmd.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val DEFAULT_MODEL = "openai/gpt-4o-mini"

@Serializable
data class ModelPricing(val prompt: String = "", val completion: String = "")

@Serializable
data class ModelInfo(
    val id: String,
    val name: String = id,
    @SerialName("context_length") val contextLength: Long? = null,
    val pricing: ModelPricing? = null,
)

/** «128K ctx» / «1M ctx»; пусто, если размер неизвестен. */
fun ModelInfo.contextLabel(): String {
    val n = contextLength ?: return ""
    return when {
        n >= 1_000_000 -> "${n / 1_000_000}M ctx"
        n >= 1_000 -> "${n / 1_000}K ctx"
        else -> "$n ctx"
    }
}

/** Цена входных токенов за 1M в USD; null если цены нет или она отрицательная
 *  (OpenRouter отдаёт -1 для роутеров с «переменной» ценой, напр. openrouter/auto). */
fun ModelInfo.pricePerMillion(): Double? =
    pricing?.prompt?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { it * 1_000_000 }

/** Цена входных токенов за 1M: «$0.15/M» / «Free»; пусто, если цены нет. */
fun ModelInfo.priceLabel(): String {
    val perMillion = pricePerMillion() ?: return ""
    if (perMillion == 0.0) return "Free"
    val text = if (perMillion == perMillion.toLong().toDouble()) {
        perMillion.toLong().toString()
    } else {
        // до 2 знаков без trailing-нулей
        ((perMillion * 100).toLong() / 100.0).toString().trimEnd('0').trimEnd('.')
    }
    return "\$$text/M"
}

/** Порог макс. цены за 1M токенов (null = любая). */
enum class PriceFilter(val maxPerMillion: Double?) { ANY(null), FREE(0.0), UNDER_1(1.0), UNDER_5(5.0) }

/** Порог мин. размера контекста (null = любой). */
enum class ContextFilter(val minContext: Long?) { ANY(null), K32(32_000), K128(128_000), M1(1_000_000) }

/** Порядок сортировки списка моделей. */
enum class SortOrder { NAME, PRICE_ASC, PRICE_DESC }

/** Сортировка; модели без цены всегда в конце (и при ↑, и при ↓). */
fun List<ModelInfo>.sortModels(order: SortOrder): List<ModelInfo> = when (order) {
    SortOrder.NAME -> sortedBy { it.name.lowercase() }
    SortOrder.PRICE_ASC -> sortedBy { it.pricePerMillion() ?: Double.MAX_VALUE }
    SortOrder.PRICE_DESC -> sortedByDescending { it.pricePerMillion() ?: -1.0 }
}

/** Поиск + фильтры по цене/контексту. Модель без нужного поля отсекается активным фильтром. */
fun List<ModelInfo>.filterModels(query: String, price: PriceFilter, context: ContextFilter): List<ModelInfo> =
    filter { m ->
        val matchesQuery = query.isBlank() ||
            m.id.contains(query, ignoreCase = true) || m.name.contains(query, ignoreCase = true)
        val matchesPrice = when (price) {
            PriceFilter.ANY -> true
            PriceFilter.FREE -> m.pricePerMillion() == 0.0
            // «≤ $N/M» — только платные в пределах порога, бесплатные не показываем
            else -> m.pricePerMillion()?.let { it > 0.0 && it <= price.maxPerMillion!! } ?: false
        }
        val matchesContext = context.minContext?.let { min -> (m.contextLength ?: -1L) >= min } ?: true
        matchesQuery && matchesPrice && matchesContext
    }

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
