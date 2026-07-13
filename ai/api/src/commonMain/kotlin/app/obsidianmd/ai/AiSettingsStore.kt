package app.obsidianmd.ai

/** Настройки AI (провайдер/модель/вкл-выкл/base URL). Реализация — в :ai:impl (SharedPrefs). */
interface AiSettingsStore {
    fun isAiEnabled(): Boolean
    fun setAiEnabled(enabled: Boolean)
    fun getProvider(): String?
    fun setProvider(id: String)
    fun getCustomBaseUrl(): String
    fun setCustomBaseUrl(url: String)
    fun getAiModel(provider: String): String
    fun setAiModel(provider: String, model: String)
}
