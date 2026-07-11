package app.obsidianmd.analytics

/** Тонкая обёртка над AppMetrica: событие + опциональные строковые параметры. */
expect object Analytics {
    fun event(name: String, params: Map<String, String> = emptyMap())
}
