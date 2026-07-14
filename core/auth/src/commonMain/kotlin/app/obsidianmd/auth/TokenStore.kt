package app.obsidianmd.auth

interface TokenStore {
    fun save(token: String)
    fun get(): String?
    fun clear()
}
