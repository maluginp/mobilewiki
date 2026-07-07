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

    override suspend fun sync(config: SyncConfig, resolver: ConflictResolver): SyncResult = withContext(io) {
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
                git.fetch().setRemote("origin")
                    .setCredentialsProvider(creds).call()
                val remoteRef = git.repository.findRef("refs/remotes/origin/${config.branch}")
                    ?: return@use SyncResult.Failed("no remote branch ${config.branch}")

                val merge = git.merge()
                    .include(remoteRef)
                    .setCommit(true)
                    .call()

                // Разрешение конфликтов: .md — спрашиваем резолвер (local/server),
                // не-.md — всегда серверная версия. Неконфликтующие правки сохраняет merge.
                var conflictsResolved = 0
                var resolvedMerge = false
                if (merge.mergeStatus == org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING) {
                    val conflicting = merge.conflicts?.keys ?: emptySet()
                    conflictsResolved = conflicting.size
                    val headId = git.repository.resolve("HEAD")
                    for (path in conflicting) {
                        val stage = if (path.endsWith(".md")) {
                            val local = readBlob(git.repository, headId, path) ?: ""
                            val server = readBlob(git.repository, remoteRef.objectId, path) ?: ""
                            when (resolver.resolve(MdConflict(path, local, server))) {
                                Resolution.USE_LOCAL -> org.eclipse.jgit.api.CheckoutCommand.Stage.OURS
                                Resolution.USE_SERVER -> org.eclipse.jgit.api.CheckoutCommand.Stage.THEIRS
                            }
                        } else {
                            org.eclipse.jgit.api.CheckoutCommand.Stage.THEIRS
                        }
                        git.checkout().setStage(stage).addPath(path).call()
                    }
                    git.add().addFilepattern(".").call()
                    git.commit()
                        .setMessage("obsidian-md sync (server wins)")
                        .setAuthor(config.authorName, config.authorEmail)
                        .setCommitter(config.authorName, config.authorEmail)
                        .call()
                    resolvedMerge = true
                }

                val shouldPush = committedLocal || resolvedMerge ||
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
                else SyncResult.Synced(pushed = pushed, conflictsResolved = conflictsResolved)
            }
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e.toString())
        }
    }

    private fun readBlob(
        repo: org.eclipse.jgit.lib.Repository,
        commit: org.eclipse.jgit.lib.ObjectId?,
        path: String,
    ): String? {
        if (commit == null) return null
        org.eclipse.jgit.revwalk.RevWalk(repo).use { rw ->
            val tree = rw.parseCommit(commit).tree
            org.eclipse.jgit.treewalk.TreeWalk.forPath(repo, path, tree)?.use { tw ->
                return String(repo.open(tw.getObjectId(0)).bytes)
            }
        }
        return null
    }
}
