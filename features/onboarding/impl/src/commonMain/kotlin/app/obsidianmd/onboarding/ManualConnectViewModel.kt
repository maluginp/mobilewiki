package app.obsidianmd.onboarding

import androidx.lifecycle.ViewModel
import app.obsidianmd.auth.TokenStore

class ManualConnectViewModel(private val store: TokenStore) : ViewModel() {
    /** Сохраняет непустой токен и возвращает URL для перехода на шаг проверки доступа. */
    fun connect(url: String, token: String): String {
        if (token.isNotBlank()) store.save(token.trim())
        return url.trim()
    }
}
