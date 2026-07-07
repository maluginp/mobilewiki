package app.obsidianmd.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UiConflictResolver : ConflictResolver {
    private val _pending = MutableStateFlow<MdConflict?>(null)
    val pending: StateFlow<MdConflict?> = _pending.asStateFlow()

    private var deferred: CompletableDeferred<Resolution>? = null

    override suspend fun resolve(conflict: MdConflict): Resolution {
        val d = CompletableDeferred<Resolution>()
        deferred = d
        _pending.value = conflict
        val result = d.await()
        _pending.value = null
        return result
    }

    fun choose(resolution: Resolution) {
        deferred?.complete(resolution)
    }
}
