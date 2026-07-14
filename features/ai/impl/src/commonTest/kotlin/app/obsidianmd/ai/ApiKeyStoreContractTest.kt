package app.obsidianmd.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiKeyStoreContractTest {
    @Test
    fun empty_then_save_get() {
        val s = FakeApiKeyStore()
        assertNull(s.getKey("openrouter"))
        s.saveKey("openrouter", "sk-1")
        assertEquals("sk-1", s.getKey("openrouter"))
    }

    @Test
    fun keys_are_isolated_per_provider() {
        val s = FakeApiKeyStore()
        s.saveKey("openrouter", "sk-or")
        s.saveKey("provod", "sk_prov")
        assertEquals("sk-or", s.getKey("openrouter"))
        assertEquals("sk_prov", s.getKey("provod"))
    }
}
