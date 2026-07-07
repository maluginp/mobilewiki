package app.obsidianmd.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val store: RepoSettingsStore) {
    private val _url = MutableStateFlow(store.getRemoteUrl() ?: "")
    val url: StateFlow<String> = _url.asStateFlow()

    fun save(url: String) {
        store.setRemoteUrl(url)
        _url.value = url
    }
}
