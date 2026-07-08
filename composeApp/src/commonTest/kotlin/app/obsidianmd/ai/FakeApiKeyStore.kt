package app.obsidianmd.ai

class FakeApiKeyStore : ApiKeyStore {
    private var key: String? = null
    override fun getKey(): String? = key
    override fun saveKey(key: String) { this.key = key }
}
