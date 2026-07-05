package com.example.bamachat.data

import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.shared.core.AiChatMessage
import com.example.bamachat.shared.core.AiChatRole

/**
 * Preparation layer for sharedCore AI chat messages.
 * This is intentionally not used for request routing yet.
 */
fun ChatMessage.toAiChatMessageOrNull(): AiChatMessage? {
    val content = text.trim()
    if (content.isBlank()) return null

    val mappedRole = when (role?.trim()?.lowercase()) {
        null, "" -> if (isUser) AiChatRole.USER else AiChatRole.ASSISTANT
        "user" -> AiChatRole.USER
        "assistant" -> AiChatRole.ASSISTANT
        "system", "developer" -> return null
        else -> return null
    }

    return AiChatMessage(
        role = mappedRole,
        text = content
    )
}
