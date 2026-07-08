package com.example.bamachat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AgentLoopRequestFactoryTest {

    @Test
    fun buildAgentChatRequest_keepsModelMessagesAndToolsUnchanged() {
        val messages = listOf(
            OpenRouterMessage(role = "system", content = "sys"),
            OpenRouterMessage(role = "user", content = "hello")
        )
        val tools = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf("name" to "search_web")
            )
        )

        val request = AgentLoopRequestFactory.buildAgentChatRequest(
            model = "openrouter/free",
            messages = messages,
            toolDefs = tools
        )

        assertEquals("openrouter/free", request.model)
        assertSame(messages, request.messages)
        assertEquals(messages, request.messages)
        assertSame(tools, request.tools)
        assertEquals(tools, request.tools)
        assertEquals(false, request.stream)
        assertEquals("auto", request.toolChoice)
        assertEquals(2048, request.maxTokens)
    }

    @Test
    fun createToolResultMessage_setsRoleToolCallIdAndContent() {
        val message = AgentLoopRequestFactory.createToolResultMessage(
            toolCallId = "call_123",
            content = "result payload"
        )

        assertEquals("tool", message.role)
        assertEquals("call_123", message.toolCallId)
        assertEquals("result payload", message.content)
    }
}

