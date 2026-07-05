package com.example.bamachat.shared.core

enum class AiChatRole {
    USER,
    ASSISTANT
}

data class AiChatMessage(
    val role: AiChatRole,
    val text: String
)

enum class AiProviderId {
    OPENROUTER,
    OLLAMA,
    GROQ,
    CEREBRAS,
    TOGETHER,
    OPENCODE
}

data class AiChatRequest(
    val provider: AiProviderId,
    val model: String,
    val messages: List<AiChatMessage>,
    val quickAction: QuickActionSuggestion,
    val runtimeDecision: ExtensionRuntimeDecision? = null,
    val maxTokens: Int = 1200,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

data class AiChatResponse(
    val provider: AiProviderId,
    val model: String,
    val message: AiChatMessage
)
