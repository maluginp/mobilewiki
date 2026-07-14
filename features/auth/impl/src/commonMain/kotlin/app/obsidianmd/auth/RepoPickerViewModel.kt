package app.obsidianmd.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RepoPickerState {
    data object Loading : RepoPickerState
    data class Loaded(val repos: List<GitHubRepo>) : RepoPickerState
    data class Error(val reason: String) : RepoPickerState
}

class RepoPickerViewModel(
    private val repos: RepoList,
    private val token: () -> String?,
) : ViewModel() {
    private val _state = MutableStateFlow<RepoPickerState>(RepoPickerState.Loading)
    val state: StateFlow<RepoPickerState> = _state.asStateFlow()

    // Выбранный репозиторий как наблюдаемое состояние — UI-колбэк в ViewModel не держим.
    private val _picked = MutableStateFlow<String?>(null)
    val picked: StateFlow<String?> = _picked.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = RepoPickerState.Loading
            _state.value = try {
                RepoPickerState.Loaded(repos.list(token().orEmpty()))
            } catch (e: Exception) {
                RepoPickerState.Error(e.message ?: e.toString())
            }
        }
    }

    fun pick(cloneUrl: String) { _picked.value = cloneUrl }
}
