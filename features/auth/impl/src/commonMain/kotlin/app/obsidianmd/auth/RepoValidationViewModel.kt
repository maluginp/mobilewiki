package app.obsidianmd.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.obsidianmd.analytics.Analytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ValidationState {
    data object Checking : ValidationState
    data object Ok : ValidationState
    data class Denied(val status: Int) : ValidationState
    data class Unknown(val reason: String) : ValidationState
}

class RepoValidationViewModel(
    private val access: RepoAccess,
    private val token: () -> String?,
) : ViewModel() {
    private val _state = MutableStateFlow<ValidationState>(ValidationState.Checking)
    val state: StateFlow<ValidationState> = _state.asStateFlow()

    fun validate(url: String) {
        viewModelScope.launch {
            _state.value = ValidationState.Checking
            _state.value = when (val r = access.check(token().orEmpty(), url)) {
                is AccessResult.Ok -> {
                    Analytics.event("repo_connected")
                    ValidationState.Ok
                }
                is AccessResult.Denied -> ValidationState.Denied(r.status)
                is AccessResult.Unknown -> ValidationState.Unknown(r.reason)
            }
        }
    }
}
