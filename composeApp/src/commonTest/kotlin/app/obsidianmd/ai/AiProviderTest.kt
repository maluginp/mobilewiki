package app.obsidianmd.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiProviderTest {
    @Test
    fun by_id_resolves_known_and_falls_back_to_default() {
        assertEquals(AiProvider.OPENROUTER, AiProvider.byId("openrouter"))
        assertEquals(AiProvider.PROVOD, AiProvider.byId("provod"))
        assertEquals(AiProvider.DEFAULT, AiProvider.byId(null))
        assertEquals(AiProvider.DEFAULT, AiProvider.byId("nope"))
    }

    @Test
    fun urls_are_openai_compatible_paths() {
        AiProvider.entries.forEach {
            assertTrue(it.chatUrl.endsWith("/chat/completions"), "${it.id} chatUrl")
            assertTrue(it.modelsUrl.endsWith("/models"), "${it.id} modelsUrl")
        }
    }
}
