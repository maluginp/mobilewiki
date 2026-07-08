package app.obsidianmd.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
data class ChatResponse(val choices: List<Choice>)

@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatMessage>, val tools: JsonElement)

private const val MODEL = "openai/gpt-4o-mini"

// Описания инструментов для function calling.
internal val TOOLS: JsonElement = Json.parseToJsonElement(
    """
    [
      {"type":"function","function":{"name":"search_notes","description":"Найти заметки по подстроке (имя+содержимое)","parameters":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}},
      {"type":"function","function":{"name":"read_note","description":"Прочитать заметку по имени файла","parameters":{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}}},
      {"type":"function","function":{"name":"write_note","description":"Создать или перезаписать заметку (требует подтверждения пользователя)","parameters":{"type":"object","properties":{"name":{"type":"string"},"content":{"type":"string"}},"required":["name","content"]}}}
    ]
    """.trimIndent(),
)

class OpenRouterClient(
    private val http: HttpClient,
    private val apiKey: String,
) : ChatClient {
    override suspend fun chat(messages: List<ChatMessage>): ChatResponse =
        http.post("https://openrouter.ai/api/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(MODEL, messages, TOOLS))
        }.body()
}
