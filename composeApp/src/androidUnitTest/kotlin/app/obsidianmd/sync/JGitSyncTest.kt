package app.obsidianmd.sync

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JGitSyncTest {

    private fun config(bare: File, local: File) = SyncConfig(
        remoteUrl = bare.toURI().toString(),
        localPath = local.absolutePath,
        branch = "main",
        token = null,
    )

    @Test
    fun first_sync_shallow_clones() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val result = JGitSync().sync(config(bare, local))
        assertEquals(SyncResult.Cloned, result)
        assertTrue(File(local, "welcome.md").exists())
        assertTrue(File(local, ".git/shallow").exists(), "должен быть shallow-клон")
    }

    @Test
    fun local_edit_is_committed_and_pushed() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg) // clone
        File(local, "welcome.md").writeText("# Welcome\n\nlocal edit\n")

        val result = sync.sync(cfg)

        assertTrue(result is SyncResult.Synced && result.pushed, "должен быть push")
        assertTrue(readFromServer(bare, "welcome.md").contains("local edit"),
            "правка должна оказаться на сервере (push из shallow-клона)")
    }
}
