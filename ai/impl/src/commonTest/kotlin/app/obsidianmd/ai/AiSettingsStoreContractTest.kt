package app.obsidianmd.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiSettingsStoreContractTest {
    private fun store(): AiSettingsStore = FakeAiSettingsStore()

    @Test fun ai_disabled_by_default() { assertFalse(store().isAiEnabled()) }

    @Test fun ai_enabled_persists() {
        val s = store(); s.setAiEnabled(true); assertTrue(s.isAiEnabled())
    }

    @Test fun model_is_per_provider() {
        val s = store()
        s.setAiModel("openrouter", "a"); s.setAiModel("provod", "b")
        assertEquals("a", s.getAiModel("openrouter"))
        assertEquals("b", s.getAiModel("provod"))
    }

    @Test fun base_url_persists() {
        val s = store(); s.setCustomBaseUrl("https://h/v1"); assertEquals("https://h/v1", s.getCustomBaseUrl())
    }
}
