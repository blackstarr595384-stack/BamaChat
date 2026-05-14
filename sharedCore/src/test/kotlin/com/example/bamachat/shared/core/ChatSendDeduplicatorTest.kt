package com.example.bamachat.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSendDeduplicatorTest {

    @Test
    fun normalizeForDedupCollapsesWhitespace() {
        val normalized = ChatSendDeduplicator.normalizeForDedup("  hi   there \n\t friend  ")
        assertEquals("hi there friend", normalized)
    }

    @Test
    fun isDuplicateSendReturnsTrueWithinWindowForSameConversation() {
        val result = ChatSendDeduplicator.isDuplicateSend(
            lastNormalizedText = "hello",
            lastConversationId = "c1",
            lastSentAtMs = 1_000L,
            newNormalizedText = "hello",
            newConversationId = "c1",
            nowMs = 1_900L
        )
        assertTrue(result)
    }

    @Test
    fun isDuplicateSendReturnsFalseWhenConversationChanges() {
        val result = ChatSendDeduplicator.isDuplicateSend(
            lastNormalizedText = "hello",
            lastConversationId = "c1",
            lastSentAtMs = 1_000L,
            newNormalizedText = "hello",
            newConversationId = "c2",
            nowMs = 1_500L
        )
        assertFalse(result)
    }
}
