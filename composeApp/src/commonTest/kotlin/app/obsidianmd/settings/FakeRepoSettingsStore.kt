package app.obsidianmd.settings

class FakeRepoSettingsStore : RepoSettingsStore {
    private var url: String? = null
    private var aiEnabled: Boolean = false
    private var provider: String? = null
    private val models = mutableMapOf<String, String>()
    override fun getRemoteUrl(): String? = url
    override fun setRemoteUrl(url: String) { this.url = url }
    override fun isAiEnabled(): Boolean = aiEnabled
    override fun setAiEnabled(enabled: Boolean) { this.aiEnabled = enabled }
    override fun getProvider(): String? = provider
    override fun setProvider(id: String) { this.provider = id }
    override fun getAiModel(provider: String): String = models[provider] ?: ""
    override fun setAiModel(provider: String, model: String) { models[provider] = model }
}
