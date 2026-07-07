package app.obsidianmd.auth

class FakeTokenStore : TokenStore {
    private var token: String? = null
    override fun save(token: String) { this.token = token }
    override fun get(): String? = token
    override fun clear() { token = null }
}
