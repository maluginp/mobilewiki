package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
    fun isAiEnabled(): Boolean
    fun setAiEnabled(enabled: Boolean)
}
