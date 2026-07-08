package app.obsidianmd.sync

class BackgroundSyncRunner(
    private val gitSync: GitSync,
    private val syncConfigProvider: () -> SyncConfig?,
) {
    private val localWins = ConflictResolver { Resolution.USE_LOCAL }

    suspend fun run(): SyncResult {
        val cfg = syncConfigProvider() ?: return SyncResult.Failed("репозиторий не настроен")
        return gitSync.sync(cfg, localWins)
    }
}
