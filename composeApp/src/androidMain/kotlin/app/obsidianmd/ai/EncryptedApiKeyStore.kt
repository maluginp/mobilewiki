package app.obsidianmd.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedApiKeyStore(context: Context) : ApiKeyStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "obsidian_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun getKey(): String? = prefs.getString("openrouter_key", null)
    override fun saveKey(key: String) { prefs.edit().putString("openrouter_key", key).apply() }
}
