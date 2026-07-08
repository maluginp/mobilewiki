package app.obsidianmd.ai

interface ApiKeyStore {
    fun getKey(): String?
    fun saveKey(key: String)
}
