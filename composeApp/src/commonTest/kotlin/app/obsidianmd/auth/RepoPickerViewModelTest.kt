package app.obsidianmd.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeRepoList(val result: Result<List<GitHubRepo>>) : RepoList {
    override suspend fun list(token: String): List<GitHubRepo> = result.getOrThrow()
}

class RepoPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val two = listOf(
        GitHubRepo("me/a", "https://github.com/me/a.git", false),
        GitHubRepo("me/b", "https://github.com/me/b.git", true),
    )

    @Test
    fun load_success_moves_to_loaded() = runTest(dispatcher) {
        val vm = RepoPickerViewModel(FakeRepoList(Result.success(two)), { "t" })
        vm.load()
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is RepoPickerState.Loaded && s.repos.size == 2)
    }

    @Test
    fun load_failure_moves_to_error() = runTest(dispatcher) {
        val vm = RepoPickerViewModel(FakeRepoList(Result.failure(RuntimeException("no net"))), { "t" })
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value is RepoPickerState.Error)
    }

    @Test
    fun pick_exposes_selected_clone_url() = runTest(dispatcher) {
        val vm = RepoPickerViewModel(FakeRepoList(Result.success(two)), { "t" })
        vm.pick("https://github.com/me/b.git")
        assertEquals("https://github.com/me/b.git", vm.picked.value)
    }
}
