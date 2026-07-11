package app.obsidianmd.ai

/** Ключи хранятся отдельно на каждого провайдера (provider = AiProvider.id). */
interface ApiKeyStore {
    fun getKey(provider: String): String?
    fun saveKey(provider: String, key: String)
}
