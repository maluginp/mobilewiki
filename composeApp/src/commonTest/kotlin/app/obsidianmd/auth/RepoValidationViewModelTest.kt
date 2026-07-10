package app.obsidianmd.auth

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

private class FakeAccess(val result: AccessResult) : RepoAccess {
    override suspend fun check(token: String, url: String): AccessResult = result
}

class RepoValidationViewModelTest {
    @Test
    fun validate_ok() = runTest {
        val vm = RepoValidationViewModel(FakeAccess(AccessResult.Ok), { "t" }, this)
        vm.validate("https://github.com/me/notes.git")
        advanceUntilIdle()
        assertTrue(vm.state.value is ValidationState.Ok)
    }

    @Test
    fun validate_denied() = runTest {
        val vm = RepoValidationViewModel(FakeAccess(AccessResult.Denied(404)), { "t" }, this)
        vm.validate("https://github.com/me/x.git")
        advanceUntilIdle()
        assertTrue(vm.state.value is ValidationState.Denied)
    }

    @Test
    fun validate_unknown() = runTest {
        val vm = RepoValidationViewModel(FakeAccess(AccessResult.Unknown("no net")), { "t" }, this)
        vm.validate("https://gitlab.com/me/x.git")
        advanceUntilIdle()
        assertTrue(vm.state.value is ValidationState.Unknown)
    }
}
