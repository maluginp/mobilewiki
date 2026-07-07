package app.obsidianmd.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private fun clientReturning(vararg bodies: String): HttpClient {
    var i = 0
    val engine = MockEngine {
        respond(
            content = bodies[minOf(i++, bodies.lastIndex)],
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}

class GitHubDeviceAuthTest {
    @Test
    fun requestDeviceCode_parses_response() = runTest {
        val http = clientReturning(
            """{"device_code":"dc","user_code":"WXYZ-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
        )
        val auth = GitHubDeviceAuth(http, "client123")
        val result = auth.requestDeviceCode()
        assertEquals("dc", result.deviceCode)
        assertEquals("WXYZ-1234", result.userCode)
        assertEquals("https://github.com/login/device", result.verificationUri)
        assertEquals(5, result.interval)
        assertEquals(900, result.expiresIn)
    }
}
