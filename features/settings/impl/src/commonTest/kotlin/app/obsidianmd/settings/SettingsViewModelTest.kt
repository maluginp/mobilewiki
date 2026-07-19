package app.obsidianmd.settings

import app.obsidianmd.settings.presentation.SettingsViewModel
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
    fun refresh_rereads_url_and_writable_after_change() {
        val store = FakeRepoSettingsStore()
        val vm = SettingsViewModel(store)                 // пусто, writable=true (VM переживает экран)
        assertEquals("", vm.state.value.url)
        store.setRemoteUrl("https://ro.git"); store.setWritable(false)
        vm.refresh()
        assertEquals("https://ro.git", vm.state.value.url)
        assertEquals(false, vm.state.value.writable)
    }

    @Test
    fun use_local_clears_remote_and_marks_done() {
        val store = FakeRepoSettingsStore().apply { setRemoteUrl("https://a.git") }
        val vm = SettingsViewModel(store)
        vm.useLocal()
        assertEquals("", store.getRemoteUrl())
        assertEquals("", vm.state.value.url)
        assertEquals(true, store.getOnboardingDone())
    }
}
