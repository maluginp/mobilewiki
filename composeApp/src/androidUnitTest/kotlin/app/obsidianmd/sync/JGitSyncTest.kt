package app.obsidianmd.sync

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JGitSyncTest {

    private val useServer = ConflictResolver { Resolution.USE_SERVER }
    private val useLocal = ConflictResolver { Resolution.USE_LOCAL }
    private val failingResolver = ConflictResolver { error("резолвер не должен вызываться для не-.md") }

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
        val result = JGitSync().sync(config(bare, local), useServer)
        assertEquals(SyncResult.Cloned, result)
        assertTrue(File(local, "welcome.md").exists())
        assertTrue(!File(local, ".git/shallow").exists(), "полный клон (не shallow)")
    }

    @Test
    fun changing_remote_url_reclones_new_repo() = runTest {
        val bareA = createSeededBareRepo()
        val bareB = createSeededBareRepo()
        pushRemoteChange(bareB, "only-in-b.md", "# B\n")
        val local = newLocalDir()
        val sync = JGitSync()

        sync.sync(config(bareA, local), useServer) // клон A
        assertTrue(File(local, "welcome.md").exists())

        val result = sync.sync(config(bareB, local), useServer) // сменили URL на B

        assertEquals(SyncResult.Cloned, result)
        assertTrue(File(local, "only-in-b.md").exists(), "должен подтянуться репозиторий B")
    }

    @Test
    fun no_changes_returns_up_to_date() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg, useServer) // clone
        val result = sync.sync(cfg, useServer) // без изменений
        assertEquals(SyncResult.UpToDate, result)
    }

    @Test
    fun local_edit_is_committed_and_pushed() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg, useServer) // clone
        File(local, "welcome.md").writeText("# Welcome\n\nlocal edit\n")

        val result = sync.sync(cfg, useServer)

        assertTrue(result is SyncResult.Synced && result.pushed, "должен быть push")
        assertTrue(readFromServer(bare, "welcome.md").contains("local edit"),
            "правка должна оказаться на сервере")
    }

    @Test
    fun merges_remote_and_local_without_conflict() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg, useServer) // clone

        pushRemoteChange(bare, "remote.md", "# Remote\n")           // сервер: новый файл
        File(local, "welcome.md").writeText("# Welcome\n\nlocal\n") // локально: другой файл

        val result = sync.sync(cfg, useServer)

        assertTrue(result is SyncResult.Synced)
        assertTrue(File(local, "remote.md").exists(), "серверный файл подтянут")
        assertTrue(File(local, "welcome.md").readText().contains("local"), "локальная правка цела")
    }

    @Test
    fun md_conflict_use_server_takes_server() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg, useServer) // clone

        pushRemoteChange(bare, "welcome.md", "# Welcome\n\nSERVER\n")
        File(local, "welcome.md").writeText("# Welcome\n\nLOCAL\n")

        val result = sync.sync(cfg, useServer)

        assertTrue(result is SyncResult.Synced)
        assertEquals(1, (result as SyncResult.Synced).conflictsResolved)
        assertTrue(File(local, "welcome.md").readText().contains("SERVER"), "серверная версия победила")
        assertTrue(!File(local, "welcome.md").readText().contains("LOCAL"))
    }

    @Test
    fun md_conflict_use_local_keeps_local() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg, useLocal) // clone

        pushRemoteChange(bare, "welcome.md", "# Welcome\n\nSERVER\n")
        File(local, "welcome.md").writeText("# Welcome\n\nLOCAL\n")

        val result = sync.sync(cfg, useLocal)

        assertTrue(result is SyncResult.Synced)
        assertTrue(File(local, "welcome.md").readText().contains("LOCAL"), "выбрана локальная версия")
        assertTrue(!File(local, "welcome.md").readText().contains("SERVER"))
    }

    @Test
    fun non_md_conflict_always_server_without_resolver() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg, useServer) // clone

        pushRemoteChange(bare, "config.txt", "SERVER\n") // не-.md, добавлен на сервере
        File(local, "config.txt").writeText("LOCAL\n")   // и локально иначе → конфликт add/add

        // failingResolver бросит, если движок попытается спросить про не-.md
        val result = sync.sync(cfg, failingResolver)

        assertTrue(result is SyncResult.Synced)
        assertTrue(File(local, "config.txt").readText().contains("SERVER"), "не-.md всегда сервер")
    }
}
