package app.obsidianmd.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.obsidianmd.analytics.Analytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class AiSettingsState(
    val provider: AiProvider = AiProvider.DEFAULT,
    val customBaseUrl: String = "",
    val apiKey: String = "",
    val aiEnabled: Boolean = false,
    val aiModel: String = "",
    val models: List<ModelInfo> = emptyList(),
    val modelsLoading: Boolean = false,
)

internal class AiSettingsViewModel(
    private val store: AiSettingsStore,
    private val apiKeyStore: ApiKeyStore,
    // Список моделей зависит от провайдера (его modelsUrl + ключ) и, для Custom, base URL.
    private val fetchModels: suspend (AiProvider, String) -> List<ModelInfo> = { _, _ -> emptyList() },
) : ViewModel() {
    private val _state = MutableStateFlow(
        AiProvider.byId(store.getProvider()).let { provider ->
            AiSettingsState(
                provider = provider,
                customBaseUrl = store.getCustomBaseUrl(),
                apiKey = apiKeyStore.getKey(provider.id) ?: "",
                aiEnabled = store.isAiEnabled(),
                aiModel = store.getAiModel(provider.id).ifBlank { provider.defaultModel },
            )
        },
    )
    val state: StateFlow<AiSettingsState> = _state.asStateFlow()

    init {
        if (_state.value.aiEnabled) loadModels()
    }

    fun setAiEnabled(enabled: Boolean) {
        Analytics.event("ai_toggled", mapOf("enabled" to enabled.toString()))
        store.setAiEnabled(enabled)
        _state.update { it.copy(aiEnabled = enabled) }
        if (enabled) loadModels()
    }

    /** Смена провайдера: подтягиваем его собственные ключ и модель, сбрасываем список моделей. */
    fun setProvider(provider: AiProvider) {
        Analytics.event("ai_provider_changed", mapOf("provider" to provider.id))
        store.setProvider(provider.id)
        _state.update {
            it.copy(
                provider = provider,
                apiKey = apiKeyStore.getKey(provider.id) ?: "",
                aiModel = store.getAiModel(provider.id).ifBlank { provider.defaultModel },
                models = emptyList(),
            )
        }
        if (_state.value.aiEnabled) loadModels(force = true)
    }

    /** Base URL для Custom-провайдера; меняет модели → перезагружаем список. */
    fun setCustomBaseUrl(url: String) {
        store.setCustomBaseUrl(url)
        _state.update { it.copy(customBaseUrl = url, models = emptyList()) }
        if (_state.value.aiEnabled && _state.value.provider.needsBaseUrl) loadModels(force = true)
    }

    fun saveKey(key: String) {
        apiKeyStore.saveKey(_state.value.provider.id, key)
        _state.update { it.copy(apiKey = key) }
    }

    fun setAiModel(model: String) {
        Analytics.event("ai_model_changed", mapOf("model" to model))
        store.setAiModel(_state.value.provider.id, model)
        _state.update { it.copy(aiModel = model) }
    }

    /** Лениво подгрузить список при открытии пикера (если ещё не загружен). */
    fun ensureModels() = loadModels()

    /** Форс-перезагрузка списка (pull-to-refresh на экране выбора модели). */
    fun reloadModels() = loadModels(force = true)

    // Список моделей тянем лениво (endpoint публичный, ключ — опционально). Пустой результат
    // не кэшируется — при следующем включении AI/открытии пикера попробуем снова.
    // force=true игнорирует кэш (pull-to-refresh).
    private fun loadModels(force: Boolean = false) {
        if (_state.value.modelsLoading) return
        if (!force && _state.value.models.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(modelsLoading = true) }
            val list = runCatching { fetchModels(_state.value.provider, _state.value.customBaseUrl) }
                .getOrDefault(emptyList())
            _state.update { it.copy(models = list, modelsLoading = false) }
        }
    }
}
