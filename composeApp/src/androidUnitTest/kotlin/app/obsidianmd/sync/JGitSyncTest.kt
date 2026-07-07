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
    fun first_sync_clones() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val result = JGitSync().sync(config(bare, local))
        assertEquals(SyncResult.Cloned, result)
        assertTrue(File(local, "welcome.md").exists())
        assertTrue(!File(local, ".git/shallow").exists(), "полный клон (не shallow)")
    }

    @Test
    fun no_changes_returns_up_to_date() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg) // clone
        val result = sync.sync(cfg) // без изменений
        assertEquals(SyncResult.UpToDate, result)
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

    @Test
    fun merges_remote_and_local_without_conflict() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg) // clone

        pushRemoteChange(bare, "remote.md", "# Remote\n")           // сервер: новый файл
        File(local, "welcome.md").writeText("# Welcome\n\nlocal\n") // локально: другой файл

        val result = sync.sync(cfg)

        assertTrue(result is SyncResult.Synced)
        assertTrue(File(local, "remote.md").exists(), "серверный файл подтянут")
        assertTrue(File(local, "welcome.md").readText().contains("local"), "локальная правка цела")
    }

    @Test
    fun conflict_server_wins_and_counts() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg) // clone

        pushRemoteChange(bare, "welcome.md", "# Welcome\n\nSERVER\n") // сервер правит welcome.md
        File(local, "welcome.md").writeText("# Welcome\n\nLOCAL\n")    // локально тот же файл иначе

        val result = sync.sync(cfg)

        assertTrue(result is SyncResult.Synced)
        assertEquals(1, (result as SyncResult.Synced).conflictsResolved)
        assertTrue(File(local, "welcome.md").readText().contains("SERVER"), "серверная версия победила")
        assertTrue(!File(local, "welcome.md").readText().contains("LOCAL"), "локальная версия перезаписана")
    }
}
