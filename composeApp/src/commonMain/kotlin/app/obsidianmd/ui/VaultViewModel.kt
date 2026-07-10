package app.obsidianmd.ui

import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultEntry
import app.obsidianmd.vault.VaultFile
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
    val allFiles: List<VaultFile> = emptyList(),
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

    private val _documents = MutableStateFlow<List<app.obsidianmd.vault.DocRef>>(emptyList())
    val documents: StateFlow<List<app.obsidianmd.vault.DocRef>> = _documents.asStateFlow()

    /** Загрузить список документов (с заголовками) для пикера ссылок — чтение на IO. */
    fun loadDocuments() {
        scope.launch { _documents.value = withContext(io) { repo.documents() } }
    }

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
        // ponytail: allFiles обходит всё дерево на каждый вход в папку — ок для личного vault;
        // кэшировать, если станет медленно на больших хранилищах.
        val entries = withContext(io) { repo.listEntries(dir) }
        val all = withContext(io) { repo.allFiles() }
        _state.value = _state.value.copy(
            entries = entries,
            allFiles = all,
            currentDir = dir,
            atRoot = repo.isRoot(dir),
            query = "",
            results = emptyList(),
        )
    }

    // Стек истории просмотра: открытие из списка сбрасывает его, wikilink — добавляет,
    // «Назад» возвращает к предыдущей заметке (а не сразу к списку).
    private val history = ArrayDeque<MdFile>()

    /** Открыть файл по абсолютному пути (навигация по wikilink) — добавляет в историю. */
    fun openPath(absPath: String) {
        val file = MdFile(absPath.substringAfterLast('/'), absPath)
        history.addLast(file)
        loadSelected(file)
    }

    /** Байты файла (для отрисовки картинок-эмбедов). */
    fun bytesOf(absPath: String): ByteArray = repo.readBytes(absPath)

    /** Открыть файл из списка — начинает историю заново. */
    fun open(file: MdFile) {
        history.clear()
        history.addLast(file)
        loadSelected(file)
    }

    private fun loadSelected(file: MdFile) {
        scope.launch {
            _state.value = _state.value.copy(selected = file, loading = true)
            val text = withContext(io) { repo.readFile(file.path) }
            _state.value = _state.value.copy(content = text, loading = false)
        }
    }

    fun back() {
        if (history.isNotEmpty()) history.removeLast()
        val prev = history.lastOrNull()
        if (prev == null) {
            _state.value = _state.value.copy(selected = null, content = "")
        } else {
            loadSelected(prev)
        }
    }

    fun saveFile(path: String, content: String) {
        scope.launch {
            withContext(io) { repo.writeFile(path, content) }
            _state.value = _state.value.copy(content = content)
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
        scope.launch {
            val results = withContext(io) { repo.search(query) }
            if (_state.value.query == query) { // не перезаписываем более свежим запросом
                _state.value = _state.value.copy(results = results)
            }
        }
    }
}
