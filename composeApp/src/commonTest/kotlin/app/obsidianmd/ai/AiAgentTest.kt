package app.obsidianmd.ai

import app.obsidianmd.vault.VaultRepository
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val root = "/vault".toPath()
private fun repo(): VaultRepository {
    val fs = FakeFileSystem()
    fs.createDirectories(root)
    fs.write(root / "welcome.md") { writeUtf8("привет проект") }
    return VaultRepository(fs, root)
}

class AiAgentTest {
    @Test
    fun search_tool_then_answer() = runTest {
        val client = ScriptedClient(
            listOf(
                toolResp("search_notes", """{"query":"проект"}"""),
                answer("нашёл welcome.md"),
            ),
        )
        val agent = AiAgent(client, repo(), { _, _ -> true })
        val result = agent.ask(listOf(ChatMessage("user", "найди проект")))
        assertEquals(AiResult.Answer("нашёл welcome.md"), result)
    }

    @Test
    fun write_tool_confirmed_writes_file() = runTest {
        val r = repo()
        val client = ScriptedClient(
            listOf(
                toolResp("write_note", """{"name":"new.md","content":"тело"}"""),
                answer("сохранил"),
            ),
        )
        val agent = AiAgent(client, r, { _, _ -> true })
        agent.ask(listOf(ChatMessage("user", "создай new.md")))
        assertEquals("тело", r.readFile(r.pathFor("new.md")))
    }

    @Test
    fun write_tool_rejected_does_not_write() = runTest {
        val r = repo()
        val client = ScriptedClient(
            listOf(
                toolResp("write_note", """{"name":"new.md","content":"тело"}"""),
                answer("ок, отменил"),
            ),
        )
        val agent = AiAgent(client, r, { _, _ -> false })
        agent.ask(listOf(ChatMessage("user", "создай new.md")))
        assertTrue(!r.listMarkdownFiles().any { it.name == "new.md" })
    }

    @Test
    fun client_error_fails_instead_of_crashing() = runTest {
        val client = object : ChatClient {
            override suspend fun chat(messages: List<ChatMessage>): ChatResponse =
                throw OpenRouterException("No auth credentials found")
        }
        val result = AiAgent(client, repo(), { _, _ -> true }).ask(listOf(ChatMessage("user", "hi")))
        assertEquals(AiResult.Failed("No auth credentials found"), result)
    }

    @Test
    fun prepends_system_prompt_instructing_wikilinks() = runTest {
        val client = ScriptedClient(listOf(answer("ok")))
        AiAgent(client, repo(), { _, _ -> true }).ask(listOf(ChatMessage("user", "hi")))
        val system = client.lastSent.firstOrNull()
        assertEquals("system", system?.role)
        assertTrue(system?.content?.contains("[[") == true, "system prompt must mention [[wikilink]] usage")
    }

    @Test
    fun exceeding_max_steps_fails() = runTest {
        val loop = List(10) { toolResp("search_notes", """{"query":"x"}""") }
        val agent = AiAgent(ScriptedClient(loop), repo(), { _, _ -> true }, maxSteps = 3)
        assertTrue(agent.ask(listOf(ChatMessage("user", "loop"))) is AiResult.Failed)
    }
}
