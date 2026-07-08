package app.obsidianmd.ai

interface ChatClient {
    suspend fun chat(messages: List<ChatMessage>): ChatResponse
}
