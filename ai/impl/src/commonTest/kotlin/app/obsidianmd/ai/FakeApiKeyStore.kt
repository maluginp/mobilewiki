package app.obsidianmd.ai

class FakeApiKeyStore : ApiKeyStore {
    private val keys = mutableMapOf<String, String>()
    override fun getKey(provider: String): String? = keys[provider]
    override fun saveKey(provider: String, key: String) { keys[provider] = key }
}
