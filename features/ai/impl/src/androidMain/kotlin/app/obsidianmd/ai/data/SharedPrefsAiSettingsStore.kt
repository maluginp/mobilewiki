package app.obsidianmd.ai

import android.content.Context

internal class SharedPrefsAiSettingsStore(context: Context) : AiSettingsStore {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    override fun isAiEnabled(): Boolean = prefs.getBoolean("ai_enabled", false)
    override fun setAiEnabled(enabled: Boolean) { prefs.edit().putBoolean("ai_enabled", enabled).apply() }
    override fun getProvider(): String? = prefs.getString("ai_provider", null)
    override fun setProvider(id: String) { prefs.edit().putString("ai_provider", id).apply() }
    override fun getCustomBaseUrl(): String = prefs.getString("ai_custom_base_url", null) ?: ""
    override fun setCustomBaseUrl(url: String) { prefs.edit().putString("ai_custom_base_url", url).apply() }
    override fun getAiModel(provider: String): String = prefs.getString(modelKey(provider), null) ?: ""
    override fun setAiModel(provider: String, model: String) { prefs.edit().putString(modelKey(provider), model).apply() }

    // ponytail: отдельный файл ai_settings, без миграции старых ключей из obsidian_settings — пользователей ещё нет.
    private fun modelKey(provider: String) =
        if (provider == "openrouter") "ai_model" else "ai_model_$provider"
}
