package app.obsidianmd.ai

import app.obsidianmd.vault.VaultRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface WriteApprover {
    suspend fun confirm(name: String, content: String): Boolean
}

sealed interface AiResult {
    data class Answer(val text: String) : AiResult
    data class Failed(val reason: String) : AiResult
}

class AiAgent(
    private val client: ChatClient,
    private val repo: VaultRepository,
    private val approver: WriteApprover,
    private val maxSteps: Int = 5,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(history: List<ChatMessage>): AiResult {
        val messages = history.toMutableList()
        repeat(maxSteps) {
            val msg = client.chat(messages).choices.firstOrNull()?.message
                ?: return AiResult.Failed("пустой ответ")
            val calls = msg.toolCalls
            if (calls.isNullOrEmpty()) return AiResult.Answer(msg.content ?: "")
            messages.add(msg)
            for (call in calls) {
                val args = json.parseToJsonElement(call.function.arguments).jsonObject
                val result = when (call.function.name) {
                    "search_notes" ->
                        repo.search(args["query"]!!.jsonPrimitive.content)
                            .joinToString(", ") { it.name }
                            .ifEmpty { "ничего не найдено" }
                    "read_note" ->
                        repo.readFile(repo.pathFor(args["name"]!!.jsonPrimitive.content))
                    "write_note" -> {
                        val name = args["name"]!!.jsonPrimitive.content
                        val content = args["content"]!!.jsonPrimitive.content
                        if (approver.confirm(name, content)) {
                            repo.writeFile(repo.pathFor(name), content)
                            "сохранено: $name"
                        } else {
                            "отклонено пользователем"
                        }
                    }
                    else -> "неизвестный инструмент"
                }
                messages.add(ChatMessage(role = "tool", content = result, toolCallId = call.id))
            }
        }
        return AiResult.Failed("превышен лимит шагов")
    }
}
