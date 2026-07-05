package com.example.bamachat.data

import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.shared.core.AiChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAiChatMessageMappingTest {
    @Test
    fun mapsUserMessage() {
        val mapped = ChatMessage(text = " Hallo ", isUser = true).toAiChatMessageOrNull()

        assertEquals(AiChatRole.USER, mapped?.role)
        assertEquals("Hallo", mapped?.text)
    }

    @Test
    fun mapsAssistantMessage() {
        val mapped = ChatMessage(text = "Antwort", isUser = false).toAiChatMessageOrNull()

        assertEquals(AiChatRole.ASSISTANT, mapped?.role)
        assertEquals("Antwort", mapped?.text)
    }

    @Test
    fun explicitRoleOverridesLegacyFlag() {
        val mapped = ChatMessage(text = "Antwort", isUser = true, role = "assistant").toAiChatMessageOrNull()

        assertEquals(AiChatRole.ASSISTANT, mapped?.role)
    }

    @Test
    fun skipsSystemAndDeveloperMessagesUntilSharedRolesExist() {
        assertNull(ChatMessage(text = "system prompt", isUser = false, role = "system").toAiChatMessageOrNull())
        assertNull(ChatMessage(text = "developer prompt", isUser = false, role = "developer").toAiChatMessageOrNull())
    }

    @Test
    fun skipsBlankAndUnknownRoleMessages() {
        assertNull(ChatMessage(text = "   ", isUser = true).toAiChatMessageOrNull())
        assertNull(ChatMessage(text = "hello", isUser = true, role = "tool").toAiChatMessageOrNull())
    }
}
