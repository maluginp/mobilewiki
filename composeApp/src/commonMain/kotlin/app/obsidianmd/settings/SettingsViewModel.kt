package app.obsidianmd.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.obsidianmd.ai.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val url: String = "",
    val openRouterKey: String = "",
    val aiEnabled: Boolean = false,
    val aiModel: String = "",
    val models: List<ModelInfo> = emptyList(),
    val modelsLoading: Boolean = false,
)

class SettingsViewModel(
    private val store: RepoSettingsStore,
    private val apiKeyStore: app.obsidianmd.ai.ApiKeyStore? = null,
    private val fetchModels: suspend () -> List<ModelInfo> = { emptyList() },
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsState(
            url = store.getRemoteUrl() ?: "",
            openRouterKey = apiKeyStore?.getKey() ?: "",
            aiEnabled = store.isAiEnabled(),
            aiModel = store.getAiModel(),
        ),
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        if (_state.value.aiEnabled) loadModels()
    }

    fun save(url: String) {
        store.setRemoteUrl(url)
        _state.update { it.copy(url = url) }
    }

    fun setAiEnabled(enabled: Boolean) {
        store.setAiEnabled(enabled)
        _state.update { it.copy(aiEnabled = enabled) }
        if (enabled) loadModels()
    }

    fun saveKey(key: String) {
        apiKeyStore?.saveKey(key)
        _state.update { it.copy(openRouterKey = key) }
    }

    fun setAiModel(model: String) {
        store.setAiModel(model)
        _state.update { it.copy(aiModel = model) }
    }

    // Список моделей тянем один раз (endpoint публичный, ключ — опционально). Пустой результат
    // не кэшируется — при следующем включении AI попробуем снова.
    private fun loadModels() {
        if (_state.value.models.isNotEmpty() || _state.value.modelsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(modelsLoading = true) }
            val list = runCatching { fetchModels() }.getOrDefault(emptyList())
            _state.update { it.copy(models = list, modelsLoading = false) }
        }
    }
}
