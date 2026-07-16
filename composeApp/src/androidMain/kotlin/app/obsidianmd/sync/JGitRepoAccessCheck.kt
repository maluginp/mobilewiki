package app.obsidianmd.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

class JGitRepoAccessCheck(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RepoAccessCheck {
    override suspend fun check(url: String, token: String?): AccessResult = withContext(io) {
        try {
            val cmd = Git.lsRemoteRepository().setRemote(url).setHeads(true)
            token?.takeIf { it.isNotBlank() }?.let {
                cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(it, ""))
            }
            cmd.call()
            AccessResult.Ok
        } catch (e: org.eclipse.jgit.api.errors.TransportException) {
            AccessResult.Denied(e.message ?: "transport error")
        } catch (e: Exception) {
            AccessResult.Unknown(e.message ?: e.toString())
        }
    }
}
