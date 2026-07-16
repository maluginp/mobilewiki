package app.obsidianmd.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualConnectViewModelTest {
    @Test fun saves_non_blank_token_and_returns_url() {
        val store = FakeTokenStore()
        val vm = ManualConnectViewModel(store)
        val url = vm.connect("https://gitlab.com/x/y.git", "glpat-abc")
        assertEquals("https://gitlab.com/x/y.git", url)
        assertEquals("glpat-abc", store.get())
    }

    @Test fun blank_token_does_not_overwrite_store() {
        val store = FakeTokenStore().apply { save("existing") }
        val vm = ManualConnectViewModel(store)
        val url = vm.connect("https://github.com/x/y.git", "")
        assertEquals("https://github.com/x/y.git", url)
        // Пустой токен (публичный репо) не затирает существующий.
        assertEquals("existing", store.get())
    }

    @Test fun blank_token_leaves_empty_store_empty() {
        val store = FakeTokenStore()
        ManualConnectViewModel(store).connect("https://github.com/x/y.git", "  ")
        assertNull(store.get())
    }
}
