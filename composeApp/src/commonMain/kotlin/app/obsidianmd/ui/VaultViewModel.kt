package app.obsidianmd.ui

import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultState(
    val files: List<MdFile> = emptyList(),
    val selected: MdFile? = null,
    val content: String = "",
    val loading: Boolean = false,
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
                app.obsidianmd.sync.SyncResult.Failed("репозиторий не настроен"),
            )
            return
        }
        scope.launch {
            _syncStatus.value = SyncStatus.Running
            val result = engine.sync(cfg, resolver)
            if (result !is app.obsidianmd.sync.SyncResult.Failed) {
                val files = withContext(io) { repo.listMarkdownFiles() }
                _state.value = _state.value.copy(files = files)
            }
            _syncStatus.value = SyncStatus.Done(result)
        }
    }

    fun resolveConflict(resolution: app.obsidianmd.sync.Resolution) {
        resolver.choose(resolution)
    }

    fun refresh() {
        scope.launch {
            val files = withContext(io) { repo.listMarkdownFiles() }
            _state.value = _state.value.copy(files = files)
        }
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
}
