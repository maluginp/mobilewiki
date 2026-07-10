package app.obsidianmd.settings

class FakeRepoSettingsStore : RepoSettingsStore {
    private var url: String? = null
    private var aiEnabled: Boolean = false
    private var aiModel: String = app.obsidianmd.ai.DEFAULT_MODEL
    override fun getRemoteUrl(): String? = url
    override fun setRemoteUrl(url: String) { this.url = url }
    override fun isAiEnabled(): Boolean = aiEnabled
    override fun setAiEnabled(enabled: Boolean) { this.aiEnabled = enabled }
    override fun getAiModel(): String = aiModel
    override fun setAiModel(model: String) { this.aiModel = model }
}
