package app.obsidianmd.onboarding

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

    private val da = DeviceAuthorization("dc", "UC", "https://github.com/login/device", interval = 1, expiresIn = 100)

    @Test
    fun poll_pending_then_success() = runTest {
        val http = clientReturning(
            """{"error":"authorization_pending"}""",
            """{"error":"authorization_pending"}""",
            """{"access_token":"gho_abc"}""",
        )
        val result = GitHubDeviceAuth(http, "c").poll(da)
        assertEquals(AuthResult.Success("gho_abc"), result)
    }

    @Test
    fun poll_slow_down_then_success() = runTest {
        val http = clientReturning(
            """{"error":"slow_down"}""",
            """{"access_token":"gho_x"}""",
        )
        assertEquals(AuthResult.Success("gho_x"), GitHubDeviceAuth(http, "c").poll(da))
    }

    @Test
    fun poll_expired_token_fails() = runTest {
        val http = clientReturning("""{"error":"expired_token"}""")
        val result = GitHubDeviceAuth(http, "c").poll(da)
        assertEquals(AuthResult.Failed("expired_token"), result)
    }

    @Test
    fun poll_tolerates_transient_network_error_then_succeeds() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) throw RuntimeException("dns blip")
            respond(
                """{"access_token":"gho_ok"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val result = GitHubDeviceAuth(http, "c").poll(da)
        assertEquals(AuthResult.Success("gho_ok"), result)
    }
}
