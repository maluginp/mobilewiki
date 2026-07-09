package app.obsidianmd.settings

import android.content.Context

class SharedPrefsRepoSettingsStore(context: Context) : RepoSettingsStore {
    private val prefs = context.getSharedPreferences("obsidian_settings", Context.MODE_PRIVATE)
    override fun getRemoteUrl(): String? = prefs.getString("remote_url", null)
    override fun setRemoteUrl(url: String) { prefs.edit().putString("remote_url", url).apply() }
    override fun isAiEnabled(): Boolean = prefs.getBoolean("ai_enabled", false)
    override fun setAiEnabled(enabled: Boolean) { prefs.edit().putBoolean("ai_enabled", enabled).apply() }
}
