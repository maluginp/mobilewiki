package app.obsidianmd.settings

class FakeRepoSettingsStore : RepoSettingsStore {
    private var url: String? = null
    private var aiEnabled: Boolean = false
    override fun getRemoteUrl(): String? = url
    override fun setRemoteUrl(url: String) { this.url = url }
    override fun isAiEnabled(): Boolean = aiEnabled
    override fun setAiEnabled(enabled: Boolean) { this.aiEnabled = enabled }
}
