package app.obsidianmd.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun cfg() = SyncConfig(remoteUrl = "file:///x", localPath = "/l", token = null)

private class RecordingGitSync(val result: SyncResult) : GitSync {
    var called = false
    override suspend fun sync(config: SyncConfig, resolver: ConflictResolver): SyncResult {
        called = true
        return result
    }
}

class BackgroundSyncRunnerTest {
    @Test
    fun no_config_fails_without_calling_engine() = runTest {
        val engine = RecordingGitSync(SyncResult.Cloned)
        val runner = BackgroundSyncRunner(engine) { null }
        val result = runner.run()
        assertTrue(!engine.called)
        assertTrue(result is SyncResult.Failed)
    }

    @Test
    fun with_config_runs_engine() = runTest {
        val engine = RecordingGitSync(SyncResult.Synced(pushed = true, conflictsResolved = 0))
        val runner = BackgroundSyncRunner(engine) { cfg() }
        val result = runner.run()
        assertTrue(engine.called)
        assertEquals(SyncResult.Synced(true, 0), result)
    }

    @Test
    fun md_conflict_resolved_use_local() = runTest {
        val engine = object : GitSync {
            override suspend fun sync(config: SyncConfig, resolver: ConflictResolver): SyncResult {
                val r = resolver.resolve(MdConflict("n.md", "L", "S"))
                return SyncResult.Synced(pushed = true, conflictsResolved = if (r == Resolution.USE_LOCAL) 1 else 0)
            }
        }
        val runner = BackgroundSyncRunner(engine) { cfg() }
        assertEquals(SyncResult.Synced(true, 1), runner.run())
    }
}
