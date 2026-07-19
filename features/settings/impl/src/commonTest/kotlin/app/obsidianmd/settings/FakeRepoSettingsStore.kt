package app.obsidianmd.settings

class FakeRepoSettingsStore : RepoSettingsStore {
    private var url: String? = null
    private var done: Boolean = false
    private var writable: Boolean = true
    override fun getRemoteUrl(): String? = url
    override fun setRemoteUrl(url: String) { this.url = url }
    override fun getOnboardingDone(): Boolean = done
    override fun setOnboardingDone(done: Boolean) { this.done = done }
    override fun getWritable(): Boolean = writable
    override fun setWritable(writable: Boolean) { this.writable = writable }
}
