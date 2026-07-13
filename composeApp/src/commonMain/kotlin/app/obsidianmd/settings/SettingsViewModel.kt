package app.obsidianmd.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsState(val url: String = "")

class SettingsViewModel(private val store: RepoSettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState(url = store.getRemoteUrl() ?: ""))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun save(url: String) {
        store.setRemoteUrl(url)
        _state.update { it.copy(url = url) }
    }
}
