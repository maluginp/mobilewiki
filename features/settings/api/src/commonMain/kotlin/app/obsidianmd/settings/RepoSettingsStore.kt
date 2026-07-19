package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
    fun getOnboardingDone(): Boolean
    fun setOnboardingDone(done: Boolean)
    /** Есть ли право записи (push) в подключённый репозиторий. По умолчанию true. */
    fun getWritable(): Boolean
    fun setWritable(writable: Boolean)
}
