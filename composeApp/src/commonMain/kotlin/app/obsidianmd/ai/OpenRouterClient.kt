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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
      {"type":"function","function":{"name":"write_note","description":"Create or overwrite a note (requires user confirmation)","parameters":{"type":"object","properties":{"name":{"type":"string"},"content":{"type":"string"}},"required":["name","content"]}}},
      {"type":"function","function":{"name":"read_skill","description":"Read the full instructions of an available skill by its name (see the skills list in the system prompt)","parameters":{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}}}
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
