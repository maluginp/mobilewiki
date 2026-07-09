package app.obsidianmd.ui

import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultEntry
import app.obsidianmd.vault.VaultRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultState(
    val entries: List<VaultEntry> = emptyList(),
    val currentDir: String = "",
    val atRoot: Boolean = true,
    val selected: MdFile? = null,
    val content: String = "",
    val loading: Boolean = false,
    val query: String = "",
    val results: List<MdFile> = emptyList(),
)

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val result: app.obsidianmd.sync.SyncResult) : SyncStatus
}

class VaultViewModel(
    private val repo: VaultRepository,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val gitSync: app.obsidianmd.sync.GitSync? = null,
    private val syncConfigProvider: () -> app.obsidianmd.sync.SyncConfig? = { null },
    private val resolver: app.obsidianmd.sync.UiConflictResolver = app.obsidianmd.sync.UiConflictResolver(),
) {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    val pendingConflict: StateFlow<app.obsidianmd.sync.MdConflict?> = resolver.pending

    fun sync() {
        val cfg = syncConfigProvider()
        val engine = gitSync
        if (cfg == null || engine == null) {
            _syncStatus.value = SyncStatus.Done(
                app.obsidianmd.sync.SyncResult.Failed("repository not configured"),
            )
            return
        }
        scope.launch {
            _syncStatus.value = SyncStatus.Running
            val result = engine.sync(cfg, resolver)
            if (result !is app.obsidianmd.sync.SyncResult.Failed) {
                loadDir(_state.value.currentDir.ifBlank { repo.rootPath })
            }
            _syncStatus.value = SyncStatus.Done(result)
        }
    }

    fun resolveConflict(resolution: app.obsidianmd.sync.Resolution) {
        resolver.choose(resolution)
    }

    fun refresh() {
        scope.launch { loadDir(_state.value.currentDir.ifBlank { repo.rootPath }) }
    }

    fun openFolder(entry: VaultEntry) {
        if (!entry.isFolder) return
        scope.launch { loadDir(entry.path) }
    }

    fun upFolder() {
        scope.launch { loadDir(repo.parentOf(_state.value.currentDir.ifBlank { repo.rootPath })) }
    }

    private suspend fun loadDir(dir: String) {
        val entries = withContext(io) { repo.listEntries(dir) }
        _state.value = _state.value.copy(
            entries = entries,
            currentDir = dir,
            atRoot = repo.isRoot(dir),
            query = "",
            results = emptyList(),
        )
    }

    fun open(file: MdFile) {
        scope.launch {
            _state.value = _state.value.copy(selected = file, loading = true)
            val text = withContext(io) { repo.readFile(file.path) }
            _state.value = _state.value.copy(content = text, loading = false)
        }
    }

    fun back() {
        _state.value = _state.value.copy(selected = null, content = "")
    }

    fun saveFile(path: String, content: String) {
        scope.launch {
            withContext(io) { repo.writeFile(path, content) }
            _state.value = _state.value.copy(content = content)
        }
    }

    fun search(query: String) {
        scope.launch {
            val results = withContext(io) { repo.search(query) }
            _state.value = _state.value.copy(query = query, results = results)
        }
    }
}
