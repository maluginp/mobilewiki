package app.obsidianmd.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepoSettingsStoreContractTest {
    @Test
    fun empty_then_set_get() {
        val store = FakeRepoSettingsStore()
        assertNull(store.getRemoteUrl())
        store.setRemoteUrl("https://github.com/u/r.git")
        assertEquals("https://github.com/u/r.git", store.getRemoteUrl())
    }
}
