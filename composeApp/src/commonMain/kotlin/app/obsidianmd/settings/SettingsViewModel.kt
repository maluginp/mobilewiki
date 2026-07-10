package app.obsidianmd.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val store: RepoSettingsStore,
    private val apiKeyStore: app.obsidianmd.ai.ApiKeyStore? = null,
) {
    private val _url = MutableStateFlow(store.getRemoteUrl() ?: "")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _openRouterKey = MutableStateFlow(apiKeyStore?.getKey() ?: "")
    val openRouterKey: StateFlow<String> = _openRouterKey.asStateFlow()

    private val _aiEnabled = MutableStateFlow(store.isAiEnabled())
    val aiEnabled: StateFlow<Boolean> = _aiEnabled.asStateFlow()

    private val _aiModel = MutableStateFlow(store.getAiModel())
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    fun save(url: String) {
        store.setRemoteUrl(url)
        _url.value = url
    }

    fun setAiEnabled(enabled: Boolean) {
        store.setAiEnabled(enabled)
        _aiEnabled.value = enabled
    }

    fun saveKey(key: String) {
        apiKeyStore?.saveKey(key)
        _openRouterKey.value = key
    }

    fun setAiModel(model: String) {
        store.setAiModel(model)
        _aiModel.value = model
    }
}
