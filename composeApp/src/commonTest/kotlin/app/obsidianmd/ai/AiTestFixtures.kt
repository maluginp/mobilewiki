package app.obsidianmd.ai

/** Клиент, отдающий заранее заданные ответы по шагам. */
class ScriptedClient(private val steps: List<ChatResponse>) : ChatClient {
    private var i = 0
    override suspend fun chat(messages: List<ChatMessage>): ChatResponse = steps[i++]
}

fun toolResp(name: String, args: String) = ChatResponse(
    listOf(Choice(ChatMessage(role = "assistant", toolCalls = listOf(ToolCall("c-$name", FunctionCall(name, args)))))),
)

fun answer(text: String) = ChatResponse(listOf(Choice(ChatMessage(role = "assistant", content = text))))
