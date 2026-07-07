package app.obsidianmd.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreContractTest {
    @Test
    fun save_get_clear() {
        val store = FakeTokenStore()
        assertNull(store.get())
        store.save("gho_1")
        assertEquals("gho_1", store.get())
        store.clear()
        assertNull(store.get())
    }
}
