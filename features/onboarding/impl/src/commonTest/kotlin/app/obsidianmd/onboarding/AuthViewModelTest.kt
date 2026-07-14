package app.obsidianmd.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeDeviceAuth(val result: AuthResult) : DeviceAuth {
    override suspend fun requestDeviceCode() =
        DeviceAuthorization("dc", "UC-1", "https://github.com/login/device", 1, 100)
    override suspend fun poll(auth: DeviceAuthorization) = result
}

class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun login_success_saves_token_and_reports_success() = runTest(dispatcher) {
        val store = FakeTokenStore()
        val vm = AuthViewModel(FakeDeviceAuth(AuthResult.Success("gho_ok")), store)
        vm.login()
        advanceUntilIdle()
        assertEquals("gho_ok", store.get())
        assertTrue(vm.state.value is AuthState.Success)
    }

    @Test
    fun login_exposes_user_code_while_awaiting() = runTest(dispatcher) {
        val store = FakeTokenStore()
        val slow = object : DeviceAuth {
            override suspend fun requestDeviceCode() =
                DeviceAuthorization("dc", "UC-2", "https://github.com/login/device", 1, 100)
            override suspend fun poll(auth: DeviceAuthorization): AuthResult {
                delay(1000); return AuthResult.Success("t")
            }
        }
        val vm = AuthViewModel(slow, store)
        vm.login()
        runCurrent()
        val s = vm.state.value
        assertTrue(s is AuthState.AwaitingUser && s.userCode == "UC-2")
    }

    @Test
    fun login_failure_reports_failed() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeDeviceAuth(AuthResult.Failed("expired")), FakeTokenStore())
        vm.login()
        advanceUntilIdle()
        assertTrue(vm.state.value is AuthState.Failed)
    }
}
