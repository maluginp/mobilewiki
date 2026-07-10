package app.obsidianmd.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterReposTest {
    private val repos = listOf(
        GitHubRepo("me/Notes", "https://github.com/me/Notes.git", true),
        GitHubRepo("me/wiki", "https://github.com/me/wiki.git", false),
        GitHubRepo("org/docs", "https://github.com/org/docs.git", false),
    )

    @Test
    fun empty_query_returns_all() {
        assertEquals(3, filterRepos(repos, "").size)
    }

    @Test
    fun matches_substring_case_insensitive() {
        val r = filterRepos(repos, "NOT")
        assertEquals(1, r.size)
        assertEquals("me/Notes", r[0].fullName)
    }

    @Test
    fun no_match_returns_empty() {
        assertEquals(0, filterRepos(repos, "zzz").size)
    }
}
