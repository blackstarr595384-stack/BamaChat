package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.OpenRouterMessage
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.EmotionAnalyzer
import com.example.bamachat.util.EmotionSignal
import com.example.bamachat.util.KnowledgeGraphExtractor
import com.example.bamachat.util.MemoryFactExtractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

/**
 * MultiAgentViewModel: Verwaltet Multi-Agent-Collaboration
 * - Generiert Perspektiven von mehreren Personas
 * - Synthesiert zu einer gemeinsamen Antwort
 * - Nutzt ApiManager für API-Calls
 */
class MultiAgentViewModel(
    application: Application,
    private val apiManager: ApiManager,
    private val personaViewModel: PersonaViewModel
) : AndroidViewModel(application) {

    private val repo = ChatRepository(ChatDatabase.getDatabase(application).chatDao())

    private val _collaborationResult = MutableStateFlow<CollaborationResult?>(null)
    val collaborationResult: StateFlow<CollaborationResult?> = _collaborationResult

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun dismissError() {
        _errorMessage.value = null
    }

    suspend fun runCollaboration(
        userPrompt: String,
        personas: List<ChatViewModel.Persona>
    ) {
        val effective = personas.distinct().take(3).ifEmpty { listOf(ChatViewModel.Persona.ASSISTANT) }

        _isRunning.value = true
        _collaborationResult.value = null

        try {
            val perspectives = mutableListOf<Pair<ChatViewModel.Persona, String>>()

            for (persona in effective) {
                val systemPrompt = personaViewModel.getSystemPromptCached(persona)
                val result = apiManager.generateReply(systemPrompt, userPrompt)

                if (result.success && result.content.isNotBlank()) {
                    perspectives.add(persona to result.content)
                }
            }

            if (perspectives.isEmpty()) {
                _errorMessage.value = "Multi-Agent konnte keine Antworten generieren."
                _isRunning.value = false
                return
            }

            // Synthesiere Antworten
            val synthesisPrompt = buildString {
                appendLine("Erstelle eine gemeinsame Antwort basierend auf mehreren Perspektiven:")
                appendLine("Nutzerfrage: $userPrompt")
                appendLine()
                perspectives.forEach { (persona, text) ->
                    appendLine("${persona.displayName}: ${text.take(800)}")
                }
                appendLine()
                appendLine("Liefere eine synthesierte Antwort, die die Stärken aller Perspektiven kombiniert.")
            }

            val assistantSystemPrompt = personaViewModel.getSystemPromptCached(ChatViewModel.Persona.ASSISTANT)
            val synthesisResult = apiManager.generateReply(assistantSystemPrompt, synthesisPrompt)

            val synthesis = if (synthesisResult.success) synthesisResult.content else ""

            _collaborationResult.value = CollaborationResult(
                userPrompt = userPrompt,
                personas = effective,
                perspectives = perspectives,
                synthesis = synthesis
            )

            AppTelemetry.logEvent(
                "multi_agent_success",
                mapOf("agent_count" to effective.size.toString())
            )
        } catch (e: Exception) {
            _errorMessage.value = "Fehler: ${e.message}"
            AppTelemetry.logError("multi_agent", e)
        } finally {
            _isRunning.value = false
        }
    }

    data class CollaborationResult(
        val userPrompt: String,
        val personas: List<ChatViewModel.Persona>,
        val perspectives: List<Pair<ChatViewModel.Persona, String>>,
        val synthesis: String
    )
}
