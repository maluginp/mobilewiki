package app.obsidianmd.settings.presentation

import androidx.lifecycle.ViewModel
import app.obsidianmd.settings.RepoSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class SettingsState(val url: String = "", val writable: Boolean = true)

internal class SettingsViewModel(private val store: RepoSettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun readState() = SettingsState(url = store.getRemoteUrl() ?: "", writable = store.getWritable())

    /** Перечитать из стора: VM переживает экран, а репозиторий мог смениться (смена репо/доступа). */
    fun refresh() { _state.value = readState() }

    /** Переключение на локальный режим: сбрасываем remote, помечаем онбординг завершённым. */
    fun useLocal() {
        store.setRemoteUrl("")
        store.setOnboardingDone(true)
        _state.update { it.copy(url = "", writable = true) }
    }
}
