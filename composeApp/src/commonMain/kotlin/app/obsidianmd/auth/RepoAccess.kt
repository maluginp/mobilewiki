package app.obsidianmd.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

sealed interface AccessResult {
    /** Токен видит репозиторий — доступ есть. */
    data object Ok : AccessResult
    /** GitHub ответил не-2xx (404 — нет репо/нет доступа, 403 — запрещено). */
    data class Denied(val status: Int) : AccessResult
    /** Не удалось проверить: не GitHub-URL или сетевая ошибка — не блокируем. */
    data class Unknown(val reason: String) : AccessResult
}

/** Достаёт `owner/repo` из GitHub-URL (https или ssh), иначе null. */
fun parseGitHubSlug(url: String): String? {
    val m = Regex("""github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?/?$""").find(url.trim()) ?: return null
    return "${m.groupValues[1]}/${m.groupValues[2]}"
}

interface RepoAccess {
    suspend fun check(token: String, url: String): AccessResult
}

class GitHubRepoAccess(private val http: HttpClient) : RepoAccess {
    override suspend fun check(token: String, url: String): AccessResult {
        val slug = parseGitHubSlug(url) ?: return AccessResult.Unknown("not-github")
        return try {
            val resp: HttpResponse = http.get("https://api.github.com/repos/$slug") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                }
            }
            if (resp.status.isSuccess()) AccessResult.Ok else AccessResult.Denied(resp.status.value)
        } catch (e: Exception) {
            AccessResult.Unknown(e.message ?: e.toString())
        }
    }
}
