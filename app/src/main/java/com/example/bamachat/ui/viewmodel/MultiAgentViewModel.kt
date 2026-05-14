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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    companion object {
        private const val PER_AGENT_TIMEOUT_MS = 25_000L
        private const val SYNTHESIS_TIMEOUT_MS = 30_000L
    }

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
        _errorMessage.value = null
        _collaborationResult.value = null

        try {
            val perspectives = coroutineScope {
                effective.map { persona ->
                    async {
                        val systemPrompt = personaViewModel.getSystemPromptCached(persona)
                        val result = withTimeoutOrNull(PER_AGENT_TIMEOUT_MS) {
                            apiManager.generateReply(systemPrompt, userPrompt)
                        }
                        if (result?.success == true && result.content.isNotBlank()) {
                            persona to result.content
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (perspectives.isEmpty()) {
                val fallback = buildOfflineFallback(userPrompt, effective)
                _errorMessage.value = "Multi-Agent offline: kein Provider erreichbar oder Timeout."
                _collaborationResult.value = CollaborationResult(
                    userPrompt = userPrompt,
                    personas = effective,
                    perspectives = emptyList(),
                    synthesis = fallback
                )
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
            val synthesisResult = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) {
                apiManager.generateReply(assistantSystemPrompt, synthesisPrompt)
            }

            val synthesis = when {
                synthesisResult?.success == true && synthesisResult.content.isNotBlank() -> synthesisResult.content
                perspectives.isNotEmpty() -> {
                    _errorMessage.value = "Synthese fehlgeschlagen. Zeige direkte Agenten-Antworten."
                    perspectives.joinToString("\n\n") { (persona, text) ->
                        "${persona.emoji} ${persona.displayName}:\n${text.take(900)}"
                    }
                }
                else -> ""
            }

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

    private fun buildOfflineFallback(
        userPrompt: String,
        personas: List<ChatViewModel.Persona>
    ): String {
        val personaLine = personas.joinToString(", ") { "${it.emoji} ${it.displayName}" }
        return buildString {
            appendLine("Ich konnte gerade keinen Live-LLM-Provider erreichen.")
            appendLine("Aktive Agenten: $personaLine")
            appendLine()
            appendLine("Arbeitsmodus (offline fallback):")
            appendLine("1. Ziel der Anfrage erkennen")
            appendLine("2. In kleine Schritte aufteilen")
            appendLine("3. Nächste konkrete Aktion ausgeben")
            appendLine()
            appendLine("Nutzer-Prompt:")
            appendLine(userPrompt.take(800))
            appendLine()
            appendLine("Empfohlener nächster Schritt:")
            appendLine("Bitte prüfe API-Key/Provider in Einstellungen oder versuche den Prompt erneut.")
        }.trim()
    }

    data class CollaborationResult(
        val userPrompt: String,
        val personas: List<ChatViewModel.Persona>,
        val perspectives: List<Pair<ChatViewModel.Persona, String>>,
        val synthesis: String
    )
}
