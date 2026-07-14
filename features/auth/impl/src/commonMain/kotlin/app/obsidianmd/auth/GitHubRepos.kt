package app.obsidianmd.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepo(
    @SerialName("full_name") val fullName: String,
    @SerialName("clone_url") val cloneUrl: String,
    val private: Boolean = false,
)

interface RepoList {
    suspend fun list(token: String): List<GitHubRepo>
}

fun filterRepos(repos: List<GitHubRepo>, query: String): List<GitHubRepo> {
    val q = query.trim()
    if (q.isEmpty()) return repos
    return repos.filter { it.fullName.contains(q, ignoreCase = true) }
}

class GitHubRepos(private val http: HttpClient) : RepoList {
    override suspend fun list(token: String): List<GitHubRepo> {
        val resp: HttpResponse = http.get("https://api.github.com/user/repos") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append(HttpHeaders.Accept, "application/vnd.github+json")
            }
            url.parameters.append("per_page", "100")
            url.parameters.append("sort", "updated")
            url.parameters.append("affiliation", "owner,collaborator,organization_member")
        }
        if (!resp.status.isSuccess()) error("GitHub /user/repos: ${resp.status}") // не-2xx → ловит вызывающий VM
        return resp.body()
    }
}
