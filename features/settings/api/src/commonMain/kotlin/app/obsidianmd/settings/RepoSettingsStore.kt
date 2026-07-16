package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
    fun getOnboardingDone(): Boolean
    fun setOnboardingDone(done: Boolean)
}
