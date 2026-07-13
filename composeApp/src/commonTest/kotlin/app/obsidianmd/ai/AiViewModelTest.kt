package app.obsidianmd.ai

import app.obsidianmd.vault.VaultRepository
import app.obsidianmd.vault.data.createVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiViewModelTest {
    private val root = "/vault".toPath()

    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun repo(): VaultRepository {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        return createVaultRepository(fs, root)
    }

    @Test
    fun send_shows_user_and_assistant_turns() = runTest(dispatcher) {
        val client = ScriptedClient(listOf(answer("ответ")))
        val r = repo()
        val vm = AiViewModel({ history, approver -> AiAgent(client, r, approver, io = dispatcher).ask(history) })
        vm.send("привет")
        advanceUntilIdle()
        assertEquals(
            listOf("привет" to "user", "ответ" to "assistant"),
            vm.state.value.messages.map { it.text to it.role },
        )
        assertTrue(vm.state.value.status is AiStatus.Done)
    }

    @Test
    fun write_sets_pending_then_approve_completes() = runTest(dispatcher) {
        val r = repo()
        val client = ScriptedClient(
            listOf(
                toolResp("write_note", """{"name":"n.md","content":"c"}"""),
                answer("сохранил"),
            ),
        )
        val vm = AiViewModel({ history, approver -> AiAgent(client, r, approver, io = dispatcher).ask(history) })
        vm.send("создай n.md")
        runCurrent()
        assertEquals("n.md" to "c", vm.state.value.pendingWrite)
        vm.approveWrite()
        advanceUntilIdle()
        assertEquals("c", r.readFile(r.pathFor("n.md")))
    }
}
