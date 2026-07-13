package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
}
