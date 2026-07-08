package app.obsidianmd.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiKeyStoreContractTest {
    @Test
    fun empty_then_save_get() {
        val s = FakeApiKeyStore()
        assertNull(s.getKey())
        s.saveKey("sk-1")
        assertEquals("sk-1", s.getKey())
    }
}
