package app.obsidianmd.onboarding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun client(status: HttpStatusCode): HttpClient {
    val engine = MockEngine {
        if (status.isSuccessLike()) {
            respond("{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
        } else {
            respondError(status)
        }
    }
    return HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
}

private fun HttpStatusCode.isSuccessLike() = value in 200..299

class RepoAccessTest {
    @Test
    fun parse_https_with_git_suffix() {
        assertEquals("me/notes", parseGitHubSlug("https://github.com/me/notes.git"))
    }

    @Test
    fun parse_https_without_suffix() {
        assertEquals("me/notes", parseGitHubSlug("https://github.com/me/notes"))
    }

    @Test
    fun parse_ssh() {
        assertEquals("me/notes", parseGitHubSlug("git@github.com:me/notes.git"))
    }

    @Test
    fun parse_non_github_is_null() {
        assertNull(parseGitHubSlug("https://gitlab.com/me/notes.git"))
        assertNull(parseGitHubSlug("nonsense"))
    }

    @Test
    fun check_ok_when_200() = runTest {
        val r = GitHubRepoAccess(client(HttpStatusCode.OK)).check("t", "https://github.com/me/notes.git")
        assertTrue(r is AccessResult.Ok)
    }

    @Test
    fun check_denied_when_404() = runTest {
        val r = GitHubRepoAccess(client(HttpStatusCode.NotFound)).check("t", "https://github.com/me/notes.git")
        assertTrue(r is AccessResult.Denied && r.status == 404)
    }

    @Test
    fun check_unknown_when_not_github() = runTest {
        val r = GitHubRepoAccess(client(HttpStatusCode.OK)).check("t", "https://gitlab.com/me/x.git")
        assertTrue(r is AccessResult.Unknown)
    }
}
