package app.obsidianmd.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeDeviceAuth(val result: AuthResult) : DeviceAuth {
    override suspend fun requestDeviceCode() =
        DeviceAuthorization("dc", "UC-1", "https://github.com/login/device", 1, 100)
    override suspend fun poll(auth: DeviceAuthorization) = result
}

class AuthViewModelTest {
    @Test
    fun login_success_saves_token_and_reports_success() = runTest {
        val store = FakeTokenStore()
        val vm = AuthViewModel(FakeDeviceAuth(AuthResult.Success("gho_ok")), store, this)
        vm.login()
        advanceUntilIdle()
        assertEquals("gho_ok", store.get())
        assertTrue(vm.state.value is AuthState.Success)
    }

    @Test
    fun login_exposes_user_code_while_awaiting() = runTest {
        val store = FakeTokenStore()
        val slow = object : DeviceAuth {
            override suspend fun requestDeviceCode() =
                DeviceAuthorization("dc", "UC-2", "https://github.com/login/device", 1, 100)
            override suspend fun poll(auth: DeviceAuthorization): AuthResult {
                delay(1000); return AuthResult.Success("t")
            }
        }
        val vm = AuthViewModel(slow, store, this)
        vm.login()
        runCurrent()
        val s = vm.state.value
        assertTrue(s is AuthState.AwaitingUser && s.userCode == "UC-2")
    }

    @Test
    fun login_failure_reports_failed() = runTest {
        val vm = AuthViewModel(FakeDeviceAuth(AuthResult.Failed("expired")), FakeTokenStore(), this)
        vm.login()
        advanceUntilIdle()
        assertTrue(vm.state.value is AuthState.Failed)
    }
}
