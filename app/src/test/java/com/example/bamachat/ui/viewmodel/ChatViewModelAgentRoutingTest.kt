package com.example.bamachat.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelAgentRoutingTest {

    @Test
    fun builtinToolsPresentAndAgentToolsDisabledRoutesToStreaming() {
        val hasBuiltinTools = true
        val agentToolsEnabled = false

        assertFalse(ChatViewModel.shouldUseAgentLoop(hasBuiltinTools, agentToolsEnabled))
    }

    @Test
    fun builtinToolsPresentAndAgentToolsEnabledRoutesToAgentLoop() {
        val hasBuiltinTools = true
        val agentToolsEnabled = true

        assertTrue(ChatViewModel.shouldUseAgentLoop(hasBuiltinTools, agentToolsEnabled))
    }

    @Test
    fun noToolsRoutesToStreaming() {
        val hasTools = false
        val agentToolsEnabled = true

        assertFalse(ChatViewModel.shouldUseAgentLoop(hasTools, agentToolsEnabled))
    }

    @Test
    fun defaultWithoutPreferenceRoutesToStreaming() {
        val hasBuiltinTools = true
        val defaultAgentToolsEnabled = false

        assertFalse(ChatViewModel.shouldUseAgentLoop(hasBuiltinTools, defaultAgentToolsEnabled))
    }
}
