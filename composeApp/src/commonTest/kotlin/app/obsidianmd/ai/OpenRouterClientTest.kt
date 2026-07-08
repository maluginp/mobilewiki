package app.obsidianmd.ai

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

private fun client(body: String): OpenRouterClient {
    val engine = MockEngine {
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val http = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    return OpenRouterClient(http, "sk-test")
}

class OpenRouterClientTest {
    @Test
    fun parses_plain_answer() = runTest {
        val resp = client("""{"choices":[{"message":{"role":"assistant","content":"привет"}}]}""")
            .chat(listOf(ChatMessage(role = "user", content = "hi")))
        assertEquals("привет", resp.choices.first().message.content)
    }

    @Test
    fun parses_tool_call() = runTest {
        val body = """{"choices":[{"message":{"role":"assistant","tool_calls":[
            {"id":"c1","function":{"name":"search_notes","arguments":"{\"query\":\"x\"}"}}]}}]}"""
        val resp = client(body).chat(listOf(ChatMessage(role = "user", content = "найди x")))
        val call = resp.choices.first().message.toolCalls!!.first()
        assertEquals("search_notes", call.function.name)
    }
}
