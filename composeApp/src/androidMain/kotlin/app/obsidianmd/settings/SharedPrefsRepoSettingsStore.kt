package app.obsidianmd.settings

import android.content.Context
import app.obsidianmd.ai.DEFAULT_MODEL

class SharedPrefsRepoSettingsStore(context: Context) : RepoSettingsStore {
    private val prefs = context.getSharedPreferences("obsidian_settings", Context.MODE_PRIVATE)
    override fun getRemoteUrl(): String? = prefs.getString("remote_url", null)
    override fun setRemoteUrl(url: String) { prefs.edit().putString("remote_url", url).apply() }
    override fun isAiEnabled(): Boolean = prefs.getBoolean("ai_enabled", false)
    override fun setAiEnabled(enabled: Boolean) { prefs.edit().putBoolean("ai_enabled", enabled).apply() }
    override fun getAiModel(): String = prefs.getString("ai_model", null) ?: DEFAULT_MODEL
    override fun setAiModel(model: String) { prefs.edit().putString("ai_model", model).apply() }
}
