package com.example.bamachat.shared.core

enum class AiChatRole {
    USER,
    ASSISTANT
}

data class AiChatMessage(
    val role: AiChatRole,
    val text: String
)
