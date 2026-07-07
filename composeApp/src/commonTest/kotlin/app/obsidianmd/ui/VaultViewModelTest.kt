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
import kotlin.test.assertTrue

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

    private class FakeGitSync(val result: app.obsidianmd.sync.SyncResult) : app.obsidianmd.sync.GitSync {
        var called = false
        override suspend fun sync(
            config: app.obsidianmd.sync.SyncConfig,
            resolver: app.obsidianmd.sync.ConflictResolver,
        ): app.obsidianmd.sync.SyncResult {
            called = true
            return result
        }
    }

    private fun syncConfig() = app.obsidianmd.sync.SyncConfig(
        remoteUrl = "file:///tmp/x", localPath = "/tmp/local", token = null,
    )

    @Test
    fun sync_success_sets_done_and_refreshes() = runTest {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.write(root / "a.md") { writeUtf8("# A") }
        val fake = FakeGitSync(app.obsidianmd.sync.SyncResult.Synced(pushed = true, conflictsResolved = 0))
        val model = VaultViewModel(
            VaultRepository(fs, root), this, StandardTestDispatcher(testScheduler),
            gitSync = fake, syncConfigProvider = { syncConfig() },
        )
        model.sync()
        advanceUntilIdle()
        assertTrue(fake.called)
        assertEquals(
            app.obsidianmd.sync.SyncResult.Synced(true, 0),
            (model.syncStatus.value as SyncStatus.Done).result,
        )
        assertEquals(listOf("a.md"), model.state.value.files.map { it.name })
    }

    private class ConflictingGitSync : app.obsidianmd.sync.GitSync {
        override suspend fun sync(
            config: app.obsidianmd.sync.SyncConfig,
            resolver: app.obsidianmd.sync.ConflictResolver,
        ): app.obsidianmd.sync.SyncResult {
            resolver.resolve(app.obsidianmd.sync.MdConflict("note.md", "L", "S"))
            return app.obsidianmd.sync.SyncResult.Synced(pushed = true, conflictsResolved = 1)
        }
    }

    @Test
    fun sync_conflict_exposes_pending_then_resolves() = runTest {
        val fs = FakeFileSystem(); fs.createDirectories(root)
        val resolver = app.obsidianmd.sync.UiConflictResolver()
        val model = VaultViewModel(
            VaultRepository(fs, root), this, StandardTestDispatcher(testScheduler),
            gitSync = ConflictingGitSync(), syncConfigProvider = { syncConfig() }, resolver = resolver,
        )
        model.sync()
        advanceUntilIdle()
        assertEquals("note.md", model.pendingConflict.value?.path)
        assertEquals(SyncStatus.Running, model.syncStatus.value)

        model.resolveConflict(app.obsidianmd.sync.Resolution.USE_SERVER)
        advanceUntilIdle()
        assertTrue(model.syncStatus.value is SyncStatus.Done)
        assertNull(model.pendingConflict.value)
    }

    @Test
    fun sync_without_config_fails_without_calling_engine() = runTest {
        val fs = FakeFileSystem(); fs.createDirectories(root)
        val fake = FakeGitSync(app.obsidianmd.sync.SyncResult.Cloned)
        val model = VaultViewModel(
            VaultRepository(fs, root), this, StandardTestDispatcher(testScheduler),
            gitSync = fake, syncConfigProvider = { null },
        )
        model.sync()
        advanceUntilIdle()
        assertTrue(!fake.called)
        assertTrue((model.syncStatus.value as SyncStatus.Done).result is app.obsidianmd.sync.SyncResult.Failed)
    }
}
