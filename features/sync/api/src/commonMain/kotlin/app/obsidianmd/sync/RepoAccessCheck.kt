package app.obsidianmd.sync

sealed interface AccessResult {
    /** Доступ есть — ls-remote отработал. */
    data object Ok : AccessResult
    /** Доступ запрещён или репозиторий не найден (ошибка транспорта/авторизации). */
    data class Denied(val reason: String) : AccessResult
    /** Не удалось проверить (сеть/прочее) — не блокируем жёстко, но и не пускаем как Ok. */
    data class Unknown(val reason: String) : AccessResult
}

/** Проверка доступа к git-репозиторию по URL и (опционально) токену/паролю. */
interface RepoAccessCheck {
    suspend fun check(url: String, token: String?): AccessResult
}
