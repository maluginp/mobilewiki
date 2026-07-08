package app.obsidianmd.ai

import app.obsidianmd.vault.VaultRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiViewModelTest {
    private val root = "/vault".toPath()
    private fun repo(): VaultRepository {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        return VaultRepository(fs, root)
    }

    @Test
    fun send_shows_user_and_assistant_turns() = runTest {
        val client = ScriptedClient(listOf(answer("ответ")))
        val r = repo()
        val vm = AiViewModel({ history, approver -> AiAgent(client, r, approver).ask(history) }, this)
        vm.send("привет")
        advanceUntilIdle()
        assertEquals(
            listOf("привет" to "user", "ответ" to "assistant"),
            vm.messages.value.map { it.text to it.role },
        )
        assertTrue(vm.status.value is AiStatus.Done)
    }

    @Test
    fun write_sets_pending_then_approve_completes() = runTest {
        val r = repo()
        val client = ScriptedClient(
            listOf(
                toolResp("write_note", """{"name":"n.md","content":"c"}"""),
                answer("сохранил"),
            ),
        )
        val vm = AiViewModel({ history, approver -> AiAgent(client, r, approver).ask(history) }, this)
        vm.send("создай n.md")
        runCurrent()
        assertEquals("n.md" to "c", vm.pendingWrite.value)
        vm.approveWrite()
        advanceUntilIdle()
        assertEquals("c", r.readFile(r.pathFor("n.md")))
    }
}
