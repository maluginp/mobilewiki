package app.obsidianmd.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class JGitSync(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : GitSync {

    override suspend fun sync(config: SyncConfig): SyncResult = withContext(io) {
        try {
            val dir = File(config.localPath)
            val creds = config.token?.let { UsernamePasswordCredentialsProvider(it, "") }
            if (!File(dir, ".git").exists()) {
                Git.cloneRepository()
                    .setURI(config.remoteUrl)
                    .setDirectory(dir)
                    .setBranch(config.branch)
                    .setBranchesToClone(listOf("refs/heads/${config.branch}"))
                    .setCloneAllBranches(false)
                    .setDepth(1)
                    .setCredentialsProvider(creds)
                    .call()
                    .close()
                return@withContext SyncResult.Cloned
            }
            Git.open(dir).use { git ->
                git.add().addFilepattern(".").call()
                git.add().addFilepattern(".").setUpdate(true).call()
                val committedLocal = if (!git.status().call().isClean) {
                    git.commit()
                        .setMessage("obsidian-md sync")
                        .setAuthor(config.authorName, config.authorEmail)
                        .setCommitter(config.authorName, config.authorEmail)
                        .call()
                    true
                } else {
                    false
                }
                if (committedLocal) {
                    git.push().setRemote("origin").setCredentialsProvider(creds).call()
                    SyncResult.Synced(pushed = true, conflictsResolved = 0)
                } else {
                    SyncResult.UpToDate
                }
            }
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e.toString())
        }
    }
}
