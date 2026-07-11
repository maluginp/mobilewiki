package app.obsidianmd.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.obsidianmd.analytics.Analytics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatTurn(val role: String, val text: String)

sealed interface AiStatus {
    data object Idle : AiStatus
    data object Thinking : AiStatus
    data object Done : AiStatus
    data class Failed(val reason: String) : AiStatus
}

data class AiState(
    val messages: List<ChatTurn> = emptyList(),
    val status: AiStatus = AiStatus.Idle,
    val pendingWrite: Pair<String, String>? = null,
)

class AiViewModel(
    private val runAgent: suspend (List<ChatMessage>, WriteApprover) -> AiResult,
) : ViewModel() {
    private val _state = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = _state.asStateFlow()

    private var writeDecision: CompletableDeferred<Boolean>? = null

    private val approver = WriteApprover { name, content ->
        val d = CompletableDeferred<Boolean>()
        writeDecision = d
        _state.update { it.copy(pendingWrite = name to content) }
        val ok = d.await()
        _state.update { it.copy(pendingWrite = null) }
        ok
    }

    fun send(text: String) {
        Analytics.event("ai_message")
        _state.update { it.copy(messages = it.messages + ChatTurn("user", text), status = AiStatus.Thinking) }
        viewModelScope.launch {
            val history = _state.value.messages.map { ChatMessage(role = it.role, content = it.text) }
            when (val r = runAgent(history, approver)) {
                is AiResult.Answer -> {
                    Analytics.event("ai_response")
                    _state.update { it.copy(messages = it.messages + ChatTurn("assistant", r.text), status = AiStatus.Done) }
                }
                is AiResult.Failed -> {
                    Analytics.event("ai_error", mapOf("reason" to r.reason))
                    _state.update { it.copy(status = AiStatus.Failed(r.reason)) }
                }
            }
        }
    }

    fun approveWrite() { Analytics.event("ai_write_approved"); writeDecision?.complete(true) }
    fun rejectWrite() { Analytics.event("ai_write_rejected"); writeDecision?.complete(false) }
}
