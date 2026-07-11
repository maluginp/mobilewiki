package app.obsidianmd.settings

import android.content.Context

class SharedPrefsRepoSettingsStore(context: Context) : RepoSettingsStore {
    private val prefs = context.getSharedPreferences("obsidian_settings", Context.MODE_PRIVATE)
    override fun getRemoteUrl(): String? = prefs.getString("remote_url", null)
    override fun setRemoteUrl(url: String) { prefs.edit().putString("remote_url", url).apply() }
    override fun isAiEnabled(): Boolean = prefs.getBoolean("ai_enabled", false)
    override fun setAiEnabled(enabled: Boolean) { prefs.edit().putBoolean("ai_enabled", enabled).apply() }
    override fun getProvider(): String? = prefs.getString("ai_provider", null)
    override fun setProvider(id: String) { prefs.edit().putString("ai_provider", id).apply() }
    override fun getCustomBaseUrl(): String = prefs.getString("ai_custom_base_url", null) ?: ""
    override fun setCustomBaseUrl(url: String) { prefs.edit().putString("ai_custom_base_url", url).apply() }
    // openrouter оставлен на старом ключе "ai_model" ради миграции ранее выбранной модели.
    override fun getAiModel(provider: String): String = prefs.getString(modelKey(provider), null) ?: ""
    override fun setAiModel(provider: String, model: String) { prefs.edit().putString(modelKey(provider), model).apply() }

    private fun modelKey(provider: String) =
        if (provider == "openrouter") "ai_model" else "ai_model_$provider"
}
