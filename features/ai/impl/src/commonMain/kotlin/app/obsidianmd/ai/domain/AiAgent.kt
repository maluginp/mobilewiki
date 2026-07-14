package app.obsidianmd.ai

import app.obsidianmd.vault.VaultRepository
import app.obsidianmd.vault.resolveWikiLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun interface WriteApprover {
    suspend fun confirm(name: String, content: String): Boolean
}

internal sealed interface AiResult {
    data class Answer(val text: String) : AiResult
    data class Failed(val reason: String) : AiResult
}

/** Добавляет .md, если в имени файла (последний сегмент) нет расширения. */
private fun withMdExtension(name: String): String =
    if (name.substringAfterLast('/').contains('.')) name else "$name.md"

private const val SYSTEM_PROMPT =
    "You are an assistant working inside the user's personal Markdown vault. " +
        "Use the provided tools (search_notes, read_note, write_note) to find and edit notes. " +
        "IMPORTANT: whenever you mention or reference a note/file from the vault in your reply, " +
        "write it as an Obsidian wikilink in double brackets — e.g. [[note-name]] or [[folder/note]] — " +
        "never as plain text or a normal Markdown link. This makes the reference clickable for the user."

/** Базовый промпт + перечень доступных скиллов (если есть) — модель дочитывает их через read_skill. */
private fun buildSystemPrompt(skills: List<app.obsidianmd.vault.SkillMeta>): String {
    if (skills.isEmpty()) return SYSTEM_PROMPT
    val list = skills.joinToString("\n") { "- ${it.name}: ${it.description}" }
    return SYSTEM_PROMPT +
        "\n\nAvailable skills — reusable instructions for specific tasks. When a user's request " +
        "matches one, call read_skill with its name to load the full instructions, then follow them:\n" +
        list
}

internal class AiAgent(
    private val client: ChatClient,
    private val repo: VaultRepository,
    private val approver: WriteApprover,
    private val maxSteps: Int = 5,
    // Блокирующий I/O по vault уводим с главного потока (агент запускается на viewModelScope = Main).
    private val io: CoroutineDispatcher = Dispatchers.Default,
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
            val prompt = withContext(io) { buildSystemPrompt(repo.listSkills()) }
            messages.add(0, ChatMessage(role = "system", content = prompt))
        }
        repeat(maxSteps) {
            val msg = client.chat(messages).choices.firstOrNull()?.message
                ?: return AiResult.Failed("empty response")
            val calls = msg.toolCalls
            if (calls.isNullOrEmpty()) return AiResult.Answer(msg.content ?: "")
            messages.add(msg)
            for (call in calls) {
                val args = json.parseToJsonElement(call.function.arguments).jsonObject
                // Весь блокирующий доступ к vault — вне главного потока (иначе ANR на большом хранилище).
                val result = withContext(io) {
                    when (call.function.name) {
                        "search_notes" ->
                            repo.search(args["query"]!!.jsonPrimitive.content)
                                .joinToString(", ") { it.name }
                                .ifEmpty { "nothing found" }
                        "read_note" -> {
                            val name = args["name"]!!.jsonPrimitive.content
                            // Модель часто даёт имя без .md / без папки — резолвим как wikilink.
                            resolveWikiLink(name, repo.allFiles())?.let { repo.readFile(it.absPath) }
                                ?: "note not found: $name"
                        }
                        "read_skill" -> {
                            val name = args["name"]!!.jsonPrimitive.content
                            repo.readSkill(name) ?: "skill not found: $name"
                        }
                        "write_note" -> {
                            val name = args["name"]!!.jsonPrimitive.content
                            val content = args["content"]!!.jsonPrimitive.content
                            if (approver.confirm(name, content)) {
                                // Существующий файл перезаписываем по его пути; новый — добавляем .md.
                                val path = resolveWikiLink(name, repo.allFiles())?.absPath
                                    ?: repo.pathFor(withMdExtension(name))
                                repo.writeFile(path, content)
                                "saved: $name"
                            } else {
                                "rejected by user"
                            }
                        }
                        else -> "unknown tool"
                    }
                }
                messages.add(ChatMessage(role = "tool", content = result, toolCallId = call.id))
            }
        }
        return AiResult.Failed("step limit exceeded")
    }
}
