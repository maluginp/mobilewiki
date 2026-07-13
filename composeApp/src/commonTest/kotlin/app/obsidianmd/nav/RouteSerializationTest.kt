package app.obsidianmd.nav

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RouteSerializationTest {
    // Полиморфный round-trip через модуль конфига: маршрут с параметром переживает сериализацию.
    @Test
    fun note_round_trips_polymorphically() {
        val json = Json { serializersModule = navSerializersModule }
        val original: NavKey = Route.Note("vault/a.md")
        val encoded = json.encodeToString<NavKey>(original)
        val decoded = json.decodeFromString<NavKey>(encoded)
        assertEquals(original, decoded)
    }
}
