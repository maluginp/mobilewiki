package app.obsidianmd.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JGitRepoAccessCheckTest {
    @Test
    fun writable_bare_repo_reports_canWrite_true() = runTest {
        val bare = createSeededBareRepo()
        val res = JGitRepoAccessCheck().check(bare.toURI().toString(), token = null)
        assertTrue(res is AccessResult.Ok, "ожидали Ok, получили $res")
        assertEquals(true, (res as AccessResult.Ok).canWrite)
    }

    @Test
    fun unreachable_repo_is_not_ok() = runTest {
        val res = JGitRepoAccessCheck().check("file:///nonexistent/repo.git", token = null)
        assertTrue(res !is AccessResult.Ok, "ls-remote не должен проходить для несуществующего репо")
    }
}
