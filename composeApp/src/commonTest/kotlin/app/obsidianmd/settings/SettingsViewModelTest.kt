package app.obsidianmd.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @Test
    fun initial_url_from_store() {
        val store = FakeRepoSettingsStore().apply { setRemoteUrl("https://a.git") }
        val vm = SettingsViewModel(store)
        assertEquals("https://a.git", vm.url.value)
    }

    @Test
    fun initial_url_empty_when_unset() {
        assertEquals("", SettingsViewModel(FakeRepoSettingsStore()).url.value)
    }

    @Test
    fun save_persists_and_updates_state() {
        val store = FakeRepoSettingsStore()
        val vm = SettingsViewModel(store)
        vm.save("https://b.git")
        assertEquals("https://b.git", store.getRemoteUrl())
        assertEquals("https://b.git", vm.url.value)
    }

    @Test
    fun ai_disabled_by_default() {
        assertFalse(SettingsViewModel(FakeRepoSettingsStore()).aiEnabled.value)
    }

    @Test
    fun ai_enabled_initial_from_store() {
        val store = FakeRepoSettingsStore().apply { setAiEnabled(true) }
        assertTrue(SettingsViewModel(store).aiEnabled.value)
    }

    @Test
    fun set_ai_enabled_persists_and_updates_state() {
        val store = FakeRepoSettingsStore()
        val vm = SettingsViewModel(store)
        vm.setAiEnabled(true)
        assertTrue(store.isAiEnabled())
        assertTrue(vm.aiEnabled.value)
    }
}
