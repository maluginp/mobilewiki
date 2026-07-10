package app.obsidianmd.auth

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeRepoList(val result: Result<List<GitHubRepo>>) : RepoList {
    override suspend fun list(token: String): List<GitHubRepo> = result.getOrThrow()
}

class RepoPickerViewModelTest {
    private val two = listOf(
        GitHubRepo("me/a", "https://github.com/me/a.git", false),
        GitHubRepo("me/b", "https://github.com/me/b.git", true),
    )

    @Test
    fun load_success_moves_to_loaded() = runTest {
        val vm = RepoPickerViewModel(FakeRepoList(Result.success(two)), { "t" }, {}, this)
        vm.load()
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is RepoPickerState.Loaded && s.repos.size == 2)
    }

    @Test
    fun load_failure_moves_to_error() = runTest {
        val vm = RepoPickerViewModel(FakeRepoList(Result.failure(RuntimeException("no net"))), { "t" }, {}, this)
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value is RepoPickerState.Error)
    }

    @Test
    fun pick_invokes_callback_with_clone_url() = runTest {
        var picked: String? = null
        val vm = RepoPickerViewModel(FakeRepoList(Result.success(two)), { "t" }, { picked = it }, this)
        vm.pick("https://github.com/me/b.git")
        assertEquals("https://github.com/me/b.git", picked)
    }
}
