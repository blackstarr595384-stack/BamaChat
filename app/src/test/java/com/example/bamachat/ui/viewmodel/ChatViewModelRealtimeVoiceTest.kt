package com.example.bamachat.ui.viewmodel

import com.example.bamachat.voice.RealtimeFinalizedTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelRealtimeVoiceTest {
    @Test
    fun finalizedUserAndAssistantTurnsKeepStableIdsAndRoles() {
        val user = ChatViewModel.toRealtimeChatMessage(
            RealtimeFinalizedTurn("rt-user-item-1", " Frage ", true, 100L)
        )
        val assistant = ChatViewModel.toRealtimeChatMessage(
            RealtimeFinalizedTurn("rt-assistant-response-1", " Antwort ", false, 200L)
        )

        assertEquals("rt-user-item-1", user?.id)
        assertEquals("Frage", user?.text)
        assertTrue(user?.isUser == true)
        assertEquals("USER", user?.role)
        assertEquals("rt-assistant-response-1", assistant?.id)
        assertEquals("Antwort", assistant?.text)
        assertFalse(assistant?.isUser ?: true)
        assertEquals("ASSISTANT", assistant?.role)
    }

    @Test
    fun partialOrInvalidRealtimeContentCannotBecomeRoomMessages() {
        assertNull(ChatViewModel.toRealtimeChatMessage(RealtimeFinalizedTurn("", "Text", true, 1L)))
        assertNull(ChatViewModel.toRealtimeChatMessage(RealtimeFinalizedTurn("id", "   ", false, 1L)))
        assertNull(
            ChatViewModel.toRealtimeChatMessage(
                RealtimeFinalizedTurn("x".repeat(201), "Text", false, 1L)
            )
        )
    }
}
