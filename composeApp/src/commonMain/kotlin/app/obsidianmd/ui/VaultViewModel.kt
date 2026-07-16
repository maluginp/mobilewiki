package app.obsidianmd.ui

import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultEntry
import app.obsidianmd.vault.VaultFile
import app.obsidianmd.vault.VaultRepository
import app.obsidianmd.analytics.Analytics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultState(
    val entries: List<VaultEntry> = emptyList(),
    val allFiles: List<VaultFile> = emptyList(),
    val currentDir: String = "",
    val atRoot: Boolean = true,
    val selected: MdFile? = null,
    val content: String = "",
    val loading: Boolean = false,
    val query: String = "",
    val results: List<MdFile> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val pendingConflict: app.obsidianmd.sync.MdConflict? = null,
    val documents: List<app.obsidianmd.vault.DocRef> = emptyList(),
)

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val result: app.obsidianmd.sync.SyncResult) : SyncStatus
}

class VaultViewModel(
    private val repo: VaultRepository,
    private val io: CoroutineDispatcher,
    private val gitSync: app.obsidianmd.sync.GitSync? = null,
    private val syncConfigProvider: () -> app.obsidianmd.sync.SyncConfig? = { null },
    private val resolver: app.obsidianmd.sync.UiConflictResolver = app.obsidianmd.sync.UiConflictResolver(),
) : ViewModel() {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    init {
        // Конфликт «живёт» в резолвере (его дёргает движок sync) — зеркалим в единое состояние экрана.
        viewModelScope.launch {
            resolver.pending.collect { c -> _state.update { it.copy(pendingConflict = c) } }
        }
    }

    /** Загрузить список документов (с заголовками) для пикера ссылок — чтение на IO. */
    fun loadDocuments() {
        viewModelScope.launch {
            val docs = withContext(io) { repo.documents() }
            _state.update { it.copy(documents = docs) }
        }
    }

    fun sync() {
        val cfg = syncConfigProvider()
        val engine = gitSync
        if (cfg == null || engine == null) {
            _state.update {
                it.copy(syncStatus = SyncStatus.Done(app.obsidianmd.sync.SyncResult.Failed("repository not configured")))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(syncStatus = SyncStatus.Running) }
            val result = engine.sync(cfg, resolver)
            Analytics.event("sync", mapOf("result" to (result::class.simpleName ?: "unknown")))
            if (result !is app.obsidianmd.sync.SyncResult.Failed) {
                loadDir(_state.value.currentDir.ifBlank { repo.rootPath })
            }
            _state.update { it.copy(syncStatus = SyncStatus.Done(result)) }
        }
    }

    fun resolveConflict(resolution: app.obsidianmd.sync.Resolution) {
        resolver.choose(resolution)
    }

    fun refresh() {
        viewModelScope.launch { loadDir(_state.value.currentDir.ifBlank { repo.rootPath }) }
    }

    private suspend fun loadDir(dir: String) {
        // ponytail: allFiles обходит всё дерево на каждый вход в папку — ок для личного vault;
        // кэшировать, если станет медленно на больших хранилищах.
        _state.value = _state.value.copy(loading = true)
        val entries = withContext(io) { repo.listEntries(dir) }
        val all = withContext(io) { repo.allFiles() }
        _state.value = _state.value.copy(
            entries = entries,
            allFiles = all,
            currentDir = dir,
            atRoot = repo.isRoot(dir),
            query = "",
            results = emptyList(),
            loading = false,
        )
    }

    /** Байты файла (для отрисовки картинок-эмбедов). */
    fun bytesOf(absPath: String): ByteArray = repo.readBytes(absPath)

    // --- Навигация через Nav3-бэкстек: историю держит стек, не VM. ---

    /** Открыть содержимое папки (маршрут VaultList(dir)); пустой dir = корень. */
    fun openDir(dir: String) {
        viewModelScope.launch { loadDir(dir.ifBlank { repo.rootPath }) }
    }

    /** Загрузить заметку по пути (маршрут Note(path)); без внутренней истории. */
    fun openNote(path: String) {
        val name = path.substringAfterLast('/')
        Analytics.event("note_open", mapOf("source" to "nav"))
        viewModelScope.launch {
            _state.value = _state.value.copy(selected = MdFile(name, path), loading = true)
            val text = withContext(io) { repo.readFile(path) }
            _state.value = _state.value.copy(content = text, loading = false)
        }
    }

    /** Сбросить открытую заметку (пустой detail-пейн на широком экране). */
    fun clearNote() {
        _state.value = _state.value.copy(selected = null, content = "")
    }

    fun saveFile(path: String, content: String) {
        Analytics.event("note_save")
        viewModelScope.launch {
            withContext(io) { repo.writeFile(path, content) }
            _state.value = _state.value.copy(content = content)
        }
    }

    /** Создать папку с именем [rawName] в текущем каталоге и обновить список. */
    fun createFolder(rawName: String) {
        val dir = _state.value.currentDir.ifBlank { repo.rootPath }
        val path = "$dir/${rawName.trim()}"
        Analytics.event("folder_create")
        viewModelScope.launch {
            withContext(io) { repo.createFolder(path) }
            loadDir(dir)
        }
    }

    /** Создать пустую .md-заметку в текущем каталоге; [onCreated] получает путь ПОСЛЕ записи. */
    fun createNote(rawName: String, onCreated: (String) -> Unit) {
        val dir = _state.value.currentDir.ifBlank { repo.rootPath }
        val path = "$dir/" + app.obsidianmd.vault.noteFileName(rawName)
        Analytics.event("note_create")
        viewModelScope.launch {
            withContext(io) { repo.writeFile(path, "") }
            loadDir(dir)
            onCreated(path)
        }
    }

    fun search(query: String) {
        // query обновляем сразу (синхронно), результаты догоняют асинхронно —
        // иначе поле ввода отставало бы от набора (прыгала каретка, терялись буквы).
        _state.value = _state.value.copy(query = query)
        if (query.isBlank()) {
            _state.value = _state.value.copy(results = emptyList())
            return
        }
        viewModelScope.launch {
            val results = withContext(io) { repo.search(query) }
            if (_state.value.query == query) { // не перезаписываем более свежим запросом
                _state.value = _state.value.copy(results = results)
            }
        }
    }
}
