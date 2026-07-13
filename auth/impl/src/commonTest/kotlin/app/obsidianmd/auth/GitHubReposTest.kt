package app.obsidianmd.auth

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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun clientOk(body: String): HttpClient {
    val engine = MockEngine {
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    return HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
}

private fun clientStatus(status: HttpStatusCode): HttpClient {
    val engine = MockEngine { respondError(status) }
    return HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
}

class GitHubReposTest {
    @Test
    fun list_parses_repos() = runTest {
        val http = clientOk(
            """[
              {"full_name":"me/notes","clone_url":"https://github.com/me/notes.git","private":true},
              {"full_name":"org/wiki","clone_url":"https://github.com/org/wiki.git","private":false}
            ]"""
        )
        val repos = GitHubRepos(http).list("gho_token")
        assertEquals(2, repos.size)
        assertEquals("me/notes", repos[0].fullName)
        assertEquals("https://github.com/me/notes.git", repos[0].cloneUrl)
        assertTrue(repos[0].private)
        assertEquals("org/wiki", repos[1].fullName)
    }

    @Test
    fun list_empty() = runTest {
        assertTrue(GitHubRepos(clientOk("[]")).list("t").isEmpty())
    }

    @Test
    fun list_http_error_throws() = runTest {
        assertFailsWith<Exception> { GitHubRepos(clientStatus(HttpStatusCode.Unauthorized)).list("t") }
    }
}
