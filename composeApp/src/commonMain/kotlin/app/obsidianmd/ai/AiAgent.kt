package app.obsidianmd.ai

import app.obsidianmd.vault.VaultRepository
import kotlinx.coroutines.CancellationException
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

private const val SYSTEM_PROMPT =
    "You are an assistant working inside the user's personal Markdown vault. " +
        "Use the provided tools (search_notes, read_note, write_note) to find and edit notes. " +
        "IMPORTANT: whenever you mention or reference a note/file from the vault in your reply, " +
        "write it as an Obsidian wikilink in double brackets — e.g. [[note-name]] or [[folder/note]] — " +
        "never as plain text or a normal Markdown link. This makes the reference clickable for the user."

class AiAgent(
    private val client: ChatClient,
    private val repo: VaultRepository,
    private val approver: WriteApprover,
    private val maxSteps: Int = 5,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(history: List<ChatMessage>): AiResult = try {
        run(history)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AiResult.Failed(e.message ?: "request failed")
    }

    private suspend fun run(history: List<ChatMessage>): AiResult {
        val messages = history.toMutableList()
        if (messages.none { it.role == "system" }) {
            messages.add(0, ChatMessage(role = "system", content = SYSTEM_PROMPT))
        }
        repeat(maxSteps) {
            val msg = client.chat(messages).choices.firstOrNull()?.message
                ?: return AiResult.Failed("empty response")
            val calls = msg.toolCalls
            if (calls.isNullOrEmpty()) return AiResult.Answer(msg.content ?: "")
            messages.add(msg)
            for (call in calls) {
                val args = json.parseToJsonElement(call.function.arguments).jsonObject
                val result = when (call.function.name) {
                    "search_notes" ->
                        repo.search(args["query"]!!.jsonPrimitive.content)
                            .joinToString(", ") { it.name }
                            .ifEmpty { "nothing found" }
                    "read_note" ->
                        repo.readFile(repo.pathFor(args["name"]!!.jsonPrimitive.content))
                    "write_note" -> {
                        val name = args["name"]!!.jsonPrimitive.content
                        val content = args["content"]!!.jsonPrimitive.content
                        if (approver.confirm(name, content)) {
                            repo.writeFile(repo.pathFor(name), content)
                            "saved: $name"
                        } else {
                            "rejected by user"
                        }
                    }
                    else -> "unknown tool"
                }
                messages.add(ChatMessage(role = "tool", content = result, toolCallId = call.id))
            }
        }
        return AiResult.Failed("step limit exceeded")
    }
}
