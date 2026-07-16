package app.obsidianmd.ui

import app.obsidianmd.vault.FakeVaultRepository
import app.obsidianmd.vault.MdFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultViewModelTest {
    private val root = "/vault"

    // viewModelScope живёт на Dispatchers.Main — в тестах подменяем его на scheduler из runTest(dispatcher).
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm(io: CoroutineDispatcher, seed: Map<String, String> = mapOf("$root/a.md" to "# A")) =
        VaultViewModel(FakeVaultRepository(root, seed), io)

    @Test
    fun refresh_loads_entries() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(io)
        model.refresh()
        advanceUntilIdle()
        assertEquals(listOf("a.md"), model.state.value.entries.map { it.name })
        assertTrue(model.state.value.atRoot)
    }

    @Test
    fun refresh_loads_all_files_recursively() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(io, mapOf("$root/a.md" to "x", "$root/sub/b.md" to "x"))
        model.refresh(); advanceUntilIdle()
        assertEquals(listOf("a.md", "sub/b.md"), model.state.value.allFiles.map { it.relPath })
    }

    @Test
    fun openNote_loads_content_without_history() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(io, mapOf("$root/a.md" to "hello"))
        model.openNote("$root/a.md"); advanceUntilIdle()
        assertEquals("hello", model.state.value.content)
        assertEquals("a.md", model.state.value.selected?.name)
    }

    @Test
    fun openDir_lists_entries_and_sets_current_dir() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(io, mapOf("$root/sub/b.md" to "x"))
        model.openDir("$root/sub"); advanceUntilIdle()
        assertEquals(listOf("b.md"), model.state.value.entries.map { it.name })
        assertEquals("$root/sub", model.state.value.currentDir)
    }

    @Test
    fun clearNote_resets_selection_and_content() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(io, mapOf("$root/a.md" to "hello"))
        model.openNote("$root/a.md"); advanceUntilIdle()
        model.clearNote()
        assertNull(model.state.value.selected)
        assertEquals("", model.state.value.content)
    }

    @Test
    fun search_updates_query_and_results() = runTest(dispatcher) {
        val model = VaultViewModel(
            FakeVaultRepository(root, mapOf("$root/a.md" to "важный проект", "$root/b.md" to "ничего")),
            StandardTestDispatcher(testScheduler),
        )

        model.search("проект")
        advanceUntilIdle()

        assertEquals("проект", model.state.value.query)
        assertEquals(listOf("a.md"), model.state.value.results.map { it.name })
    }

    @Test
    fun save_file_persists_and_updates_content() = runTest(dispatcher) {
        val repo = FakeVaultRepository(root, mapOf("$root/a.md" to "old"))
        val model = VaultViewModel(repo, StandardTestDispatcher(testScheduler))
        val path = "$root/a.md"

        model.saveFile(path, "новый текст")
        advanceUntilIdle()

        assertEquals("новый текст", repo.readFile(path))
        assertEquals("новый текст", model.state.value.content)
    }

    @Test
    fun create_folder_creates_and_refreshes() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val repo = FakeVaultRepository(root, mapOf("$root/a.md" to "x"))
        val model = VaultViewModel(repo, io)
        model.refresh(); advanceUntilIdle()

        model.createFolder("Ideas"); advanceUntilIdle()

        assertTrue(model.state.value.entries.any { it.name == "Ideas" && it.isFolder })
    }

    @Test
    fun create_note_writes_empty_file_and_reports_path() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val repo = FakeVaultRepository(root, mapOf("$root/a.md" to "x"))
        val model = VaultViewModel(repo, io)
        model.refresh(); advanceUntilIdle()

        var created: String? = null
        model.createNote("todo") { created = it }
        advanceUntilIdle()

        assertEquals("$root/todo.md", created)
        assertEquals("", repo.readFile("$root/todo.md"))
        assertTrue(model.state.value.entries.any { it.name == "todo.md" })
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
    fun sync_success_sets_done_and_refreshes() = runTest(dispatcher) {
        val fake = FakeGitSync(app.obsidianmd.sync.SyncResult.Synced(pushed = true, conflictsResolved = 0))
        val model = VaultViewModel(
            FakeVaultRepository(root, mapOf("$root/a.md" to "# A")), StandardTestDispatcher(testScheduler),
            gitSync = fake, syncConfigProvider = { syncConfig() },
        )
        model.sync()
        advanceUntilIdle()
        assertTrue(fake.called)
        assertEquals(
            app.obsidianmd.sync.SyncResult.Synced(true, 0),
            (model.state.value.syncStatus as SyncStatus.Done).result,
        )
        assertEquals(listOf("a.md"), model.state.value.entries.map { it.name })
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
    fun sync_conflict_exposes_pending_then_resolves() = runTest(dispatcher) {
        val resolver = app.obsidianmd.sync.UiConflictResolver()
        val model = VaultViewModel(
            FakeVaultRepository(root), StandardTestDispatcher(testScheduler),
            gitSync = ConflictingGitSync(), syncConfigProvider = { syncConfig() }, resolver = resolver,
        )
        model.sync()
        advanceUntilIdle()
        assertEquals("note.md", model.state.value.pendingConflict?.path)
        assertEquals(SyncStatus.Running, model.state.value.syncStatus)

        model.resolveConflict(app.obsidianmd.sync.Resolution.USE_SERVER)
        advanceUntilIdle()
        assertTrue(model.state.value.syncStatus is SyncStatus.Done)
        assertNull(model.state.value.pendingConflict)
    }

    @Test
    fun sync_without_config_fails_without_calling_engine() = runTest(dispatcher) {
        val fake = FakeGitSync(app.obsidianmd.sync.SyncResult.Cloned)
        val model = VaultViewModel(
            FakeVaultRepository(root), StandardTestDispatcher(testScheduler),
            gitSync = fake, syncConfigProvider = { null },
        )
        model.sync()
        advanceUntilIdle()
        assertTrue(!fake.called)
        assertTrue((model.state.value.syncStatus as SyncStatus.Done).result is app.obsidianmd.sync.SyncResult.Failed)
    }
}
