package app.obsidianmd.sync

data class SyncConfig(
    val remoteUrl: String,
    val localPath: String,
    val branch: String = "main",
    val token: String? = null,
    val authorName: String = "obsidian-md",
    val authorEmail: String = "obsidian-md@localhost",
)

sealed interface SyncResult {
    data object Cloned : SyncResult
    data object UpToDate : SyncResult
    data class Synced(val pushed: Boolean, val conflictsResolved: Int) : SyncResult
    data class Failed(val reason: String) : SyncResult
}

data class MdConflict(val path: String, val local: String, val server: String)

enum class Resolution { USE_LOCAL, USE_SERVER }

fun interface ConflictResolver {
    suspend fun resolve(conflict: MdConflict): Resolution
}

interface GitSync {
    suspend fun sync(config: SyncConfig, resolver: ConflictResolver): SyncResult
}
