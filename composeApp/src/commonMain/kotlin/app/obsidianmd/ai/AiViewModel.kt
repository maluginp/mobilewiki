package app.obsidianmd.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatTurn(val role: String, val text: String)

sealed interface AiStatus {
    data object Idle : AiStatus
    data object Thinking : AiStatus
    data object Done : AiStatus
    data class Failed(val reason: String) : AiStatus
}

class AiViewModel(
    private val runAgent: suspend (List<ChatMessage>, WriteApprover) -> AiResult,
    private val scope: CoroutineScope,
) {
    private val _messages = MutableStateFlow<List<ChatTurn>>(emptyList())
    val messages: StateFlow<List<ChatTurn>> = _messages.asStateFlow()

    private val _status = MutableStateFlow<AiStatus>(AiStatus.Idle)
    val status: StateFlow<AiStatus> = _status.asStateFlow()

    private val _pendingWrite = MutableStateFlow<Pair<String, String>?>(null)
    val pendingWrite: StateFlow<Pair<String, String>?> = _pendingWrite.asStateFlow()
    private var writeDecision: CompletableDeferred<Boolean>? = null

    private val approver = WriteApprover { name, content ->
        val d = CompletableDeferred<Boolean>()
        writeDecision = d
        _pendingWrite.value = name to content
        val ok = d.await()
        _pendingWrite.value = null
        ok
    }

    fun send(text: String) {
        _messages.value = _messages.value + ChatTurn("user", text)
        _status.value = AiStatus.Thinking
        scope.launch {
            val history = _messages.value.map { ChatMessage(role = it.role, content = it.text) }
            when (val r = runAgent(history, approver)) {
                is AiResult.Answer -> {
                    _messages.value = _messages.value + ChatTurn("assistant", r.text)
                    _status.value = AiStatus.Done
                }
                is AiResult.Failed -> _status.value = AiStatus.Failed(r.reason)
            }
        }
    }

    fun approveWrite() { writeDecision?.complete(true) }
    fun rejectWrite() { writeDecision?.complete(false) }
}
