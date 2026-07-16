package app.obsidianmd.settings.presentation

import androidx.lifecycle.ViewModel
import app.obsidianmd.settings.RepoSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class SettingsState(val url: String = "")

internal class SettingsViewModel(private val store: RepoSettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState(url = store.getRemoteUrl() ?: ""))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun save(url: String) {
        store.setRemoteUrl(url)
        _state.update { it.copy(url = url) }
    }

    /** Переключение на локальный режим: сбрасываем remote, помечаем онбординг завершённым. */
    fun useLocal() {
        store.setRemoteUrl("")
        store.setOnboardingDone(true)
        _state.update { it.copy(url = "") }
    }
}
