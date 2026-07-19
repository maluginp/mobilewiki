package app.obsidianmd.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.URIish
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
            cmd.call()                       // чтение подтверждено
            AccessResult.Ok(canWrite = probeWrite(url, token))
        } catch (e: org.eclipse.jgit.api.errors.TransportException) {
            AccessResult.Denied(e.message ?: "transport error")
        } catch (e: Exception) {
            AccessResult.Unknown(e.message ?: e.toString())
        }
    }

    /**
     * Проба права записи: открываем push-соединение к git-receive-pack (advertisement refs),
     * ничего не отправляя. Read-only пользователь получает auth-ошибку → false. Ничего не мутирует.
     */
    private fun probeWrite(url: String, token: String?): Boolean = try {
        val transport = Transport.open(URIish(url))
        token?.takeIf { it.isNotBlank() }?.let {
            transport.credentialsProvider = UsernamePasswordCredentialsProvider(it, "")
        }
        try {
            transport.openPush().close()
            true
        } finally {
            transport.close()
        }
    } catch (e: Exception) {
        false
    }
}
