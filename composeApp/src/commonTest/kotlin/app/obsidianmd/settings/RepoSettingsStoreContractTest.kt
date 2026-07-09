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
    fun ai_disabled_by_default_then_toggles() {
        val store = FakeRepoSettingsStore()
        assertFalse(store.isAiEnabled())
        store.setAiEnabled(true)
        assertTrue(store.isAiEnabled())
        store.setAiEnabled(false)
        assertFalse(store.isAiEnabled())
    }
}
