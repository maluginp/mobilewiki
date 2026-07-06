package app.obsidianmd.ui

import app.obsidianmd.vault.VaultRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VaultViewModelTest {
    private val root = "/vault".toPath()

    private fun vm(scope: CoroutineScope, io: CoroutineDispatcher): VaultViewModel {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.write(root / "a.md") { writeUtf8("# A") }
        return VaultViewModel(VaultRepository(fs, root), scope, io)
    }

    @Test
    fun refresh_loads_files() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(this, io)
        model.refresh()
        advanceUntilIdle()
        assertEquals(listOf("a.md"), model.state.value.files.map { it.name })
    }

    @Test
    fun open_loads_content_back_clears_it() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(this, io)
        model.refresh(); advanceUntilIdle()
        model.open(model.state.value.files.first()); advanceUntilIdle()
        assertEquals("# A", model.state.value.content)
        model.back()
        assertNull(model.state.value.selected)
    }
}
