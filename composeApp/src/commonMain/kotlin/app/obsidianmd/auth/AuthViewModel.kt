package app.obsidianmd.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun login() {
        viewModelScope.launch {
            try {
                val da = auth.requestDeviceCode()
                _state.value = AuthState.AwaitingUser(da.userCode, da.verificationUri)
                _state.value = when (val r = auth.poll(da)) {
                    is AuthResult.Success -> {
                        store.save(r.token)
                        AuthState.Success
                    }
                    is AuthResult.Failed -> AuthState.Failed(r.reason)
                }
            } catch (e: Exception) {
                _state.value = AuthState.Failed(e.message ?: e.toString())
            }
        }
    }
}
