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
                git.fetch().setRemote("origin").setDepth(1)
                    .setCredentialsProvider(creds).call()
                val remoteRef = git.repository.findRef("refs/remotes/origin/${config.branch}")
                    ?: return@use SyncResult.Failed("no remote branch ${config.branch}")

                val merge = git.merge()
                    .include(remoteRef)
                    .setCommit(true)
                    .call()

                val shouldPush = committedLocal ||
                    merge.mergeStatus == org.eclipse.jgit.api.MergeResult.MergeStatus.MERGED
                val pushed = if (shouldPush) {
                    git.push().setRemote("origin").setCredentialsProvider(creds).call()
                    true
                } else {
                    false
                }

                val upToDate = !committedLocal && !pushed &&
                    merge.mergeStatus == org.eclipse.jgit.api.MergeResult.MergeStatus.ALREADY_UP_TO_DATE
                if (upToDate) SyncResult.UpToDate
                else SyncResult.Synced(pushed = pushed, conflictsResolved = 0)
            }
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e.toString())
        }
    }
}
