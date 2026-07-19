package app.obsidianmd.onboarding

import app.obsidianmd.auth.TokenStore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.obsidianmd.analytics.Analytics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Idle : AuthState
    data class AwaitingUser(val userCode: String, val verificationUri: String) : AuthState
    data object Success : AuthState
    data class Failed(val reason: String) : AuthState
}

class AuthViewModel(
    private val auth: DeviceAuth,
    private val store: TokenStore,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private var job: Job? = null

    /** Отмена входа: останавливаем опрос и возвращаемся к исходному состоянию (экран приветствия). */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = AuthState.Idle
    }

    fun login() {
        Analytics.event("login_start")
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val da = auth.requestDeviceCode()
                _state.value = AuthState.AwaitingUser(da.userCode, da.verificationUri)
                _state.value = when (val r = auth.poll(da)) {
                    is AuthResult.Success -> {
                        store.save(r.token)
                        Analytics.event("login_success")
                        AuthState.Success
                    }
                    is AuthResult.Failed -> {
                        Analytics.event("login_fail", mapOf("reason" to r.reason))
                        AuthState.Failed(r.reason)
                    }
                }
            } catch (e: Exception) {
                Analytics.event("login_fail", mapOf("reason" to (e.message ?: e.toString())))
                _state.value = AuthState.Failed(e.message ?: e.toString())
            }
        }
    }
}
