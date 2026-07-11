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
    fun known_providers_have_baked_openai_paths() {
        listOf(AiProvider.OPENROUTER, AiProvider.PROVOD).forEach {
            assertTrue(it.chatUrl.endsWith("/chat/completions"), "${it.id} chatUrl")
            assertTrue(it.modelsUrl.endsWith("/models"), "${it.id} modelsUrl")
        }
    }

    @Test
    fun custom_builds_urls_from_base_ignoring_trailing_slash() {
        val c = AiProvider.CUSTOM
        assertTrue(c.needsBaseUrl)
        assertEquals("https://host/v1/chat/completions", c.resolvedChatUrl("https://host/v1"))
        assertEquals("https://host/v1/models", c.resolvedModelsUrl("https://host/v1/"))
        // пустой base → пусто (запрос уйдёт в никуда, а не в "/chat/completions")
        assertEquals("", c.resolvedChatUrl("  "))
    }

    @Test
    fun known_providers_ignore_custom_base_url() {
        assertEquals(AiProvider.OPENROUTER.chatUrl, AiProvider.OPENROUTER.resolvedChatUrl("https://ignored"))
        assertEquals(AiProvider.PROVOD.modelsUrl, AiProvider.PROVOD.resolvedModelsUrl("https://ignored"))
    }
}
