package app.obsidianmd.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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

/** Цена входных токенов за 1M: «$0.15/M» / «Free»; пусто, если цены нет. */
fun ModelInfo.priceLabel(): String {
    val perToken = pricing?.prompt?.toDoubleOrNull() ?: return ""
    if (perToken == 0.0) return "Free"
    val perMillion = perToken * 1_000_000
    val text = if (perMillion == perMillion.toLong().toDouble()) {
        perMillion.toLong().toString()
    } else {
        // до 2 знаков без trailing-нулей
        ((perMillion * 100).toLong() / 100.0).toString().trimEnd('0').trimEnd('.')
    }
    return "\$$text/M"
}

@Serializable
private data class ModelsResponse(val data: List<ModelInfo>)

/** Список моделей OpenRouter (для пикера в настройках). Ключ передаётся в заголовке. */
suspend fun fetchModels(http: HttpClient, apiKey: String): List<ModelInfo> =
    http.get("https://openrouter.ai/api/v1/models") {
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

class OpenRouterClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : ChatClient {
    override suspend fun chat(messages: List<ChatMessage>): ChatResponse {
        val resp: ChatResponse = http.post("https://openrouter.ai/api/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(model, messages, TOOLS))
        }.body()
        resp.error?.let { throw OpenRouterException(it.message) }
        return resp
    }
}
