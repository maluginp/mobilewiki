package app.obsidianmd.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class FunctionCall(val name: String, val arguments: String)

@Serializable
data class ToolCall(val id: String = "", val function: FunctionCall)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class Choice(val message: ChatMessage)

@Serializable
data class ApiError(val message: String)

// choices default [] + optional error: OpenRouter returns {"error":{...}} (no choices) on
// bad key / no credits / bad model. Without the default, deserialization throws and crashes the app.
@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList(), val error: ApiError? = null)

class OpenRouterException(message: String) : Exception(message)

/** Понятное сообщение об ошибке из «чужого» тела ответа (шлюз/прокси/security policy),
 *  которое не ложится в схему ChatResponse: {"error":"..."} / {"error":{"message":"..."}} / {"message":"..."}.
 *  null, если тело — не JSON-объект или в нём нет узнаваемого поля ошибки. */
internal fun extractApiError(rawBody: String): String? {
    val obj = runCatching { Json.parseToJsonElement(rawBody) }.getOrNull() as? JsonObject ?: return null
    fun str(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    str((obj["error"] as? JsonObject)?.get("message"))?.let { return it }
    str(obj["error"])?.let { return it }
    str(obj["message"])?.let { return it }
    return null
}

@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatMessage>, val tools: JsonElement)

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

@Serializable
private data class ModelsResponse(val data: List<ModelInfo>)

/** Список моделей OpenAI-совместимого провайдера (для пикера в настройках). Ключ — в заголовке. */
suspend fun fetchModels(
    http: HttpClient,
    apiKey: String,
    modelsUrl: String = AiProvider.OPENROUTER.modelsUrl,
): List<ModelInfo> =
    http.get(modelsUrl) {
        if (apiKey.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $apiKey")
    }.body<ModelsResponse>().data.sortedBy { it.name.lowercase() }

// Описания инструментов для function calling.
internal val TOOLS: JsonElement = Json.parseToJsonElement(
    """
    [
      {"type":"function","function":{"name":"search_notes","description":"Find notes by substring (name + content)","parameters":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}},
      {"type":"function","function":{"name":"read_note","description":"Read a note by file name","parameters":{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}}},
      {"type":"function","function":{"name":"write_note","description":"Create or overwrite a note (requires user confirmation)","parameters":{"type":"object","properties":{"name":{"type":"string"},"content":{"type":"string"}},"required":["name","content"]}}}
    ]
    """.trimIndent(),
)

/**
 * Клиент любого OpenAI-совместимого провайдера (OpenRouter, provod.ai, …): различаются лишь
 * chatUrl и ключ. По умолчанию — OpenRouter (обратная совместимость со старыми вызовами).
 */
class OpenRouterClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val chatUrl: String = AiProvider.OPENROUTER.chatUrl,
) : ChatClient {
    override suspend fun chat(messages: List<ChatMessage>): ChatResponse {
        val response = http.post(chatUrl) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(model, messages, TOOLS))
        }
        // Читаем тело сами: если ответ не ложится в ChatResponse (шлюз/прокси/security policy
        // отдают свою форму с error-строкой), не роняем сырое исключение десериализатора в чат,
        // а достаём понятное сообщение.
        val raw = response.bodyAsText()
        val parsed = runCatching { LENIENT.decodeFromString<ChatResponse>(raw) }.getOrNull()
        if (parsed != null) {
            parsed.error?.let { throw OpenRouterException(it.message) }
            return parsed
        }
        throw OpenRouterException(extractApiError(raw) ?: "request failed (HTTP ${response.status.value})")
    }

    private companion object {
        val LENIENT = Json { ignoreUnknownKeys = true }
    }
}
