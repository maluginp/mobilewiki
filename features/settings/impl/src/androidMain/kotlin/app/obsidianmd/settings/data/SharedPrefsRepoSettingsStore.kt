package app.obsidianmd.settings.data

import android.content.Context
import app.obsidianmd.settings.RepoSettingsStore

// ponytail: public (не internal) — фоновый SyncWorker создаёт store напрямую, вне Koin-графа.
// Тот же прецедент, что и public EncryptedTokenStore в :auth:impl.
class SharedPrefsRepoSettingsStore(context: Context) : RepoSettingsStore {
    private val prefs = context.getSharedPreferences("obsidian_settings", Context.MODE_PRIVATE)
    override fun getRemoteUrl(): String? = prefs.getString("remote_url", null)
    override fun setRemoteUrl(url: String) { prefs.edit().putString("remote_url", url).apply() }
    override fun getOnboardingDone(): Boolean = prefs.getBoolean("onboarding_done", false)
    override fun setOnboardingDone(done: Boolean) { prefs.edit().putBoolean("onboarding_done", done).apply() }
}
