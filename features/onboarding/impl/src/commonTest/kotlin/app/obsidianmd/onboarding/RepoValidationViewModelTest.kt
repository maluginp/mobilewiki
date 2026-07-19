package app.obsidianmd.onboarding

import app.obsidianmd.sync.AccessResult
import app.obsidianmd.sync.RepoAccessCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

private class FakeAccessCheck(val result: AccessResult) : RepoAccessCheck {
    override suspend fun check(url: String, token: String?): AccessResult = result
}

class RepoValidationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun validate_ok_readwrite() = runTest(dispatcher) {
        val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Ok(canWrite = true)), { "t" })
        vm.validate("https://gitlab.com/me/notes.git"); advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is ValidationState.Ok && s.canWrite)
    }

    @Test fun validate_ok_readonly() = runTest(dispatcher) {
        val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Ok(canWrite = false)), { "t" })
        vm.validate("https://gitlab.com/me/notes.git"); advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is ValidationState.Ok && !s.canWrite)
    }

    @Test fun validate_denied() = runTest(dispatcher) {
        val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Denied("auth failed")), { "t" })
        vm.validate("https://gitlab.com/me/x.git"); advanceUntilIdle()
        assertTrue(vm.state.value is ValidationState.Denied)
    }

    @Test fun validate_unknown() = runTest(dispatcher) {
        val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Unknown("no net")), { "t" })
        vm.validate("https://example.com/me/x.git"); advanceUntilIdle()
        assertTrue(vm.state.value is ValidationState.Unknown)
    }
}
