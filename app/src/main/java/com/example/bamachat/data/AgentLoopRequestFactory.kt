package com.example.bamachat.data

object AgentLoopRequestFactory {
    fun buildAgentChatRequest(
        model: String,
        messages: List<OpenRouterMessage>,
        toolDefs: List<Map<String, Any>>
    ): OpenRouterChatRequest {
        return OpenRouterChatRequest(
            model = model,
            messages = messages,
            stream = false,
            tools = toolDefs,
            toolChoice = "auto",
            maxTokens = 2048
        )
    }

    fun createToolResultMessage(
        toolCallId: String,
        content: String
    ): OpenRouterMessage {
        return OpenRouterMessage(
            role = "tool",
            toolCallId = toolCallId,
            content = content
        )
    }
}
