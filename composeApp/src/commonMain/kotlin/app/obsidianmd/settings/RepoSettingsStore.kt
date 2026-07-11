package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
    fun isAiEnabled(): Boolean
    fun setAiEnabled(enabled: Boolean)
    fun getProvider(): String?
    fun setProvider(id: String)
    /** Base URL для провайдера Custom (вида https://host/v1). */
    fun getCustomBaseUrl(): String
    fun setCustomBaseUrl(url: String)
    /** Модель хранится отдельно на каждого провайдера; "" = не выбрана. */
    fun getAiModel(provider: String): String
    fun setAiModel(provider: String, model: String)
}
