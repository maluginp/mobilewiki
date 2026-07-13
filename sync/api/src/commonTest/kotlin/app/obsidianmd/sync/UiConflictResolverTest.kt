package app.obsidianmd.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiConflictResolverTest {
    @Test
    fun resolve_suspends_publishes_pending_then_returns_choice() = runTest {
        val resolver = UiConflictResolver()
        val conflict = MdConflict("note.md", "LOCAL", "SERVER")

        val deferred = async { resolver.resolve(conflict) }
        advanceUntilIdle()
        assertEquals(conflict, resolver.pending.value)

        resolver.choose(Resolution.USE_LOCAL)
        advanceUntilIdle()
        assertEquals(Resolution.USE_LOCAL, deferred.await())
        assertNull(resolver.pending.value)
    }
}
