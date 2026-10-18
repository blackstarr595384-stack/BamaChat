package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import com.example.bamachat.data.OpenRouterChatRequest
import com.example.bamachat.data.OpenRouterMessage
import com.example.bamachat.data.provider.ProviderId
import com.example.bamachat.data.provider.chat.ActiveChatProviderSelection
import com.example.bamachat.data.provider.chat.LegacyOllamaGuard
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LegacyOllamaRoutingTest {
    // Fail before any credential access or transport can be reached, even if the guard regresses.
    private fun manager(multiProvider: Boolean): ApiManager {
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when {
                method.name == "getString" && args?.get(0) == "ai_provider" -> "Ollama"
                method.name == "getBoolean" && args?.get(0) == "multi_provider" -> multiProvider
                else -> throw AssertionError("Unexpected preferences access: ${method.name}")
            }
        } as SharedPreferences
        val app = object : Application() {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                check(name == "settings") { "Credential storage must not be opened" }
                return preferences
            }
        }
        return ApiManager(app)
    }

    @Test fun ollamaNeverBuildsCloudFallbacksWithEitherSwitchState() {
        listOf(false, true).forEach { enabled ->
            assertTrue(manager(enabled).buildProviderFallbackList().isEmpty())
        }
    }

    @Test fun streamingStopsBeforeCredentialsAndDoesNotEmitText() = runBlocking {
        listOf(false, true).forEach { enabled ->
            val result = manager(enabled).streamChatResponse("system", emptyList(),
                { fail("No text should be emitted") }, { fail("No fallback should occur") })
            assertFalse(result.success)
            assertFalse(result.retryable)
            assertEquals(LegacyOllamaGuard.MESSAGE, result.error)
        }
    }

    @Test fun replyAndAgentCompletionCannotBypassTheBlock() = runBlocking {
        val manager = manager(true)
        assertEquals(LegacyOllamaGuard.MESSAGE, manager.generateReply("system", "test").error)
        assertNull(manager.oneShotChatCompletion(
            OpenRouterChatRequest("test-model", listOf(OpenRouterMessage("user", "test"))), "system"))
    }

    @Test fun visionAndForcedResearchCannotBypassTheBlock() = runBlocking {
        val manager = manager(true)
        assertEquals(LegacyOllamaGuard.MESSAGE, manager.analyzeImage("system", "test", "unused").error)
        assertFalse(manager.shouldUseLiveWebResearch("web: test"))
        val research = manager.runLiveWebResearch("test")
        assertFalse(research.success)
        assertFalse(research.retryable)
        assertEquals(LegacyOllamaGuard.MESSAGE, research.error)
    }

    @Test fun explicitCustomSelectionIsNotOverriddenByOldOllamaPreference() {
        val custom = ActiveChatProviderSelection.Custom(ProviderId.newCustom(), "test-model")
        assertFalse(LegacyOllamaGuard.blocksSelection("Ollama", custom))
        assertTrue(LegacyOllamaGuard.blocksSelection("Ollama", ActiveChatProviderSelection.Legacy))
    }

    @Test fun otherStandardConfigurationsRemainAvailable() {
        listOf(null, "OpenRouter", "OpenCode", "Groq", "Cerebras", "Together").forEach {
            assertFalse(LegacyOllamaGuard.blocksSelection(it, ActiveChatProviderSelection.Legacy))
        }
    }
}
