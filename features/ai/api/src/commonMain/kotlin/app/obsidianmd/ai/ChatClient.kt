package app.obsidianmd.ai

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FunctionCall(val name: String, val arguments: String)

// type="function" обязателен по OpenAI-спеке при отправке assistant.tool_calls обратно; строгие
// провайдеры (provod.ai) без него отвечают 400. @EncodeDefault — иначе значение по умолчанию
// не сериализуется (encodeDefaults=false). OpenRouter принимал и без type.
@Serializable
data class ToolCall(
    val id: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val type: String = "function",
    val function: FunctionCall,
)

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

interface ChatClient {
    suspend fun chat(messages: List<ChatMessage>): ChatResponse
}
