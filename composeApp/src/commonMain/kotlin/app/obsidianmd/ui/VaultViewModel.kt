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

class VaultViewModel(
    private val repo: VaultRepository,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

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
}
