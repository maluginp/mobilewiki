package app.obsidianmd.ai

class FakeAiSettingsStore : AiSettingsStore {
    private var aiEnabled = false
    private var provider: String? = null
    private var customBaseUrl = ""
    private val models = mutableMapOf<String, String>()
    override fun isAiEnabled() = aiEnabled
    override fun setAiEnabled(enabled: Boolean) { aiEnabled = enabled }
    override fun getProvider() = provider
    override fun setProvider(id: String) { provider = id }
    override fun getCustomBaseUrl() = customBaseUrl
    override fun setCustomBaseUrl(url: String) { customBaseUrl = url }
    override fun getAiModel(provider: String) = models[provider] ?: ""
    override fun setAiModel(provider: String, model: String) { models[provider] = model }
}
