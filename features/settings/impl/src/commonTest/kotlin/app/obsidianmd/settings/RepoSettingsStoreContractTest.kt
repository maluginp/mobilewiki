package app.obsidianmd.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepoSettingsStoreContractTest {
    @Test
    fun empty_then_set_get() {
        val store = FakeRepoSettingsStore()
        assertNull(store.getRemoteUrl())
        store.setRemoteUrl("https://github.com/u/r.git")
        assertEquals("https://github.com/u/r.git", store.getRemoteUrl())
    }

    @Test
    fun writable_defaults_true_and_persists() {
        val store = FakeRepoSettingsStore()
        assertTrue(store.getWritable())
        store.setWritable(false)
        assertFalse(store.getWritable())
        store.setWritable(true)
        assertTrue(store.getWritable())
    }
}
