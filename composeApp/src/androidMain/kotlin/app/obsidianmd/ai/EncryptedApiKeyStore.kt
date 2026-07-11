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

    // Ключ на провайдера; "openrouter" оставлен на старом имени ради обратной совместимости
    // с уже сохранённым ключом (до появления мультипровайдерности он лежал под "openrouter_key").
    override fun getKey(provider: String): String? = prefs.getString(prefKey(provider), null)
    override fun saveKey(provider: String, key: String) { prefs.edit().putString(prefKey(provider), key).apply() }

    private fun prefKey(provider: String) =
        if (provider == "openrouter") "openrouter_key" else "api_key_$provider"
}
