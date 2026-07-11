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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // Шлюз/security-policy отдаёт свою форму {"success":false,"error":"..."} — не роняем сырое
    // исключение десериализатора, а показываем понятный текст ошибки.
    @Test
    fun surfaces_gateway_error_string_instead_of_deserializer_crash() = runTest {
        val ex = assertFailsWith<OpenRouterException> {
            client("""{ "success": false, "error": "Access denied by security policy." }""")
                .chat(listOf(ChatMessage(role = "user", content = "test")))
        }
        assertEquals("Access denied by security policy.", ex.message)
    }

    // Строгие провайдеры (provod.ai) требуют type="function" в assistant.tool_calls при отправке
    // обратно — иначе 400. Значение по умолчанию должно сериализоваться (@EncodeDefault).
    @Test
    fun assistant_tool_calls_serialize_with_type_function() {
        val msg = ChatMessage(
            role = "assistant",
            toolCalls = listOf(ToolCall(id = "c1", function = FunctionCall("search_notes", "{}"))),
        )
        val json = Json.encodeToString(ChatMessage.serializer(), msg)
        assertTrue(json.contains("\"type\":\"function\""), json)
    }

    @Test
    fun extract_error_reads_common_shapes() {
        assertEquals("denied", extractApiError("""{"success":false,"error":"denied"}"""))
        assertEquals("no credits", extractApiError("""{"error":{"message":"no credits"}}"""))
        assertEquals("bad gateway", extractApiError("""{"message":"bad gateway"}"""))
        assertNull(extractApiError("<html>502</html>"))
        assertNull(extractApiError("""{"choices":[]}"""))
    }
}
