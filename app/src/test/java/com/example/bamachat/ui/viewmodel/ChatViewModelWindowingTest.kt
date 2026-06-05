package com.example.bamachat.ui.viewmodel

import com.example.bamachat.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class ChatViewModelWindowingTest {

    @Test
    fun computeWindowedMessagesKeepsLatestMessages() {
        val all = (1..10).map { index ->
            ChatMessage(
                id = "m$index",
                text = "msg $index",
                isUser = index % 2 == 0
            )
        }

        val windowed = ChatViewModel.computeWindowedMessages(all, 4)

        assertEquals(4, windowed.size)
        assertEquals("m7", windowed.first().id)
        assertEquals("m10", windowed.last().id)
    }

    @Test
    fun computeWindowedMessagesHandlesInvalidLimit() {
        val all = listOf(
            ChatMessage(id = "a", text = "A", isUser = true),
            ChatMessage(id = "b", text = "B", isUser = false)
        )
        val windowed = ChatViewModel.computeWindowedMessages(all, 0)
        assertEquals(1, windowed.size)
        assertEquals("b", windowed.first().id)
    }

    @Test
    fun computeWindowedMessagesReturnsCopyWhenWithinLimit() {
        val all = mutableListOf(
            ChatMessage(id = "a", text = "A", isUser = true),
            ChatMessage(id = "b", text = "B", isUser = false)
        )

        val windowed = ChatViewModel.computeWindowedMessages(all, 10)

        assertNotSame(all, windowed)
        all.clear()
        assertEquals(2, windowed.size)
        assertEquals("a", windowed.first().id)
    }

    @Test
    fun computeWindowedMessagesLargeHistoryKeepsTailWindow() {
        val all = (1..1_000).map { index ->
            ChatMessage(id = "m$index", text = "msg $index", isUser = index % 2 == 0)
        }

        val windowed = ChatViewModel.computeWindowedMessages(all, 280)

        assertEquals(280, windowed.size)
        assertEquals("m721", windowed.first().id)
        assertEquals("m1000", windowed.last().id)
    }
}
