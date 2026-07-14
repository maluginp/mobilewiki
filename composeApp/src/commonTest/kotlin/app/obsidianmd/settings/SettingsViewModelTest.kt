package app.obsidianmd.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelTest {
    @Test
    fun initial_url_from_store() {
        val store = FakeRepoSettingsStore().apply { setRemoteUrl("https://a.git") }
        val vm = SettingsViewModel(store)
        assertEquals("https://a.git", vm.state.value.url)
    }

    @Test
    fun initial_url_empty_when_unset() {
        assertEquals("", SettingsViewModel(FakeRepoSettingsStore()).state.value.url)
    }

    @Test
    fun save_persists_and_updates_state() {
        val store = FakeRepoSettingsStore()
        val vm = SettingsViewModel(store)
        vm.save("https://b.git")
        assertEquals("https://b.git", store.getRemoteUrl())
        assertEquals("https://b.git", vm.state.value.url)
    }
}
