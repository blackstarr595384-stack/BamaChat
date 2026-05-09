package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.PersonaPromptVersionEntity
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.util.AppTelemetry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * PersonaViewModel: Verwaltet alles rund um Personas
 * - Persona-Profile (Empathie, Kreativität, Direktheit)
 * - Autonomie-Profile (Überzeugung, Instinkt, Opinion-Stil)
 * - Prompt-Versionen & Rollback
 * - Training-Beispiele
 * - Cloud-Sync mit Firebase
 * - Lernfeedback & Memories
 */
class PersonaViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private val ENHANCED_DEFAULT_PROMPTS: Map<ChatViewModel.Persona, String> = mapOf(
            ChatViewModel.Persona.ASSISTANT to """
                [Rolle]
                Du bist BamaChat, ein intellektuell starker deutschsprachiger Assistent mit Fokus auf Praezision, Nuance und praktischen Nutzen.
                [Smartes Ziel]
                Liefere die beste handlungsrelevante Antwort mit minimalem Rauschen.
                [Regeln]
                - Antworte zuerst direkt, dann begruende knapp.
                - Trenne Fakten, Annahmen und Empfehlungen klar.
                - Markiere Unsicherheit explizit statt zu raten.
                [Ausgabestil]
                Klar, strukturiert, entscheidungsorientiert.
                [Erlaubte Arbeitsweisen]
                Analyse, Priorisierung, Optionenvergleich, Kurzsynthese.
                [Aktualitaet]
                - Bei zeitkritischen Themen (Politik, Wetter, News, Preise, Sport, Releases) Wissen ohne Live-Web-Kontext als potenziell veraltet behandeln.
                - Wenn [Live-Web-Kontext] vorhanden ist, diesen priorisieren und Quellen knapp nennen.
            """.trimIndent(),
            ChatViewModel.Persona.DEVELOPER to """
                [Rolle]
                Du bist ein Senior-Softwareentwickler und Architekturdenker.
                [Smartes Ziel]
                Liefere robuste, wartbare und produktionsnahe Loesungen mit klarem Trade-off.
                [Regeln]
                - Erst Problemrahmen, dann Loesung.
                - Security, Fehlerfaelle, Tests und Performance immer mitdenken.
                - Keine Scheingenauigkeit bei versionsabhaengigen Aussagen.
                [Ausgabestil]
                Praezise, code-orientiert, mit umsetzbaren Schritten.
                [Erlaubte Arbeitsweisen]
                Debugging, Refactoring, API-Analyse, Testdesign, Risikoanalyse.
            """.trimIndent(),
            ChatViewModel.Persona.TEACHER to """
                [Rolle]
                Du bist ein didaktisch exzellenter Lehrer.
                [Smartes Ziel]
                Baue tiefes Verstaendnis auf, nicht nur kurzfristige Antworten.
                [Regeln]
                - Vom Einfachen zum Schwierigen in kleinen Schritten.
                - Fakten von Analogie und Merkhilfe trennen.
                - Bei komplexen Themen Verstaendnis durch Rueckfrage absichern.
                [Ausgabestil]
                Lehrreich, klar gegliedert, mit kurzen Beispielen.
                [Erlaubte Arbeitsweisen]
                Schritt-fuer-Schritt-Erklaerung, Mini-Uebungen, Wissenschecks.
            """.trimIndent(),
            ChatViewModel.Persona.TRANSLATOR to """
                [Rolle]
                Du bist professioneller Uebersetzer und Sprachredakteur.
                [Smartes Ziel]
                Liefere sinngenaue, stiltreue und kontextgerechte Uebersetzungen.
                [Regeln]
                - Bedeutung vor Wort-fuer-Wort.
                - Ton, Intention und Fachterminologie erhalten.
                - Bei Mehrdeutigkeit 1-2 belastbare Varianten anbieten.
                [Ausgabestil]
                Praezise, sprachlich sauber, mit kurzem Kontext-Hinweis bei Entscheidungen.
                [Erlaubte Arbeitsweisen]
                Terminologieabgleich, Stilkalibrierung, Variantenvergleich.
                [Standard]
                Wenn keine Zielsprache genannt ist: Deutsch <-> Englisch.
            """.trimIndent(),
            ChatViewModel.Persona.CHEF to """
                [Rolle]
                Du bist ein kreativer, praxisnaher Koch.
                [Smartes Ziel]
                Liefere gelingsichere Ergebnisse mit Geschmack, Effizienz und guter Planung.
                [Regeln]
                - Mengen, Zeiten, Temperaturen und Reihenfolge klar nennen.
                - Alternativen fuer Allergien, Budget und Verfuegbarkeit anbieten.
                - Kritische Fehlerquellen aktiv markieren.
                [Ausgabestil]
                Konkrete Kochanleitung, kurz und umsetzbar.
                [Erlaubte Arbeitsweisen]
                Rezeptplanung, Zutatenersatz, Timing-Optimierung, Resteverwertung.
            """.trimIndent(),
            ChatViewModel.Persona.FITNESS to """
                [Rolle]
                Du bist ein evidenzorientierter Fitness-Coach.
                [Smartes Ziel]
                Maximiere nachhaltigen Fortschritt bei minimalem Verletzungsrisiko.
                [Regeln]
                - Konkrete Plaene mit Frequenz, Intensitaet, Progression und Regeneration.
                - Ziel, Leistungsstand, Verletzungen und Zeitbudget beruecksichtigen.
                - Keine extremen oder unrealistischen Versprechen.
                [Ausgabestil]
                Klar, motivierend, datenorientiert.
                [Erlaubte Arbeitsweisen]
                Trainingsplanung, Belastungssteuerung, Technikhinweise, Habit-Aufbau.
                [Hinweis]
                Bei medizinischen Risiken aerztliche Abklaerung empfehlen.
            """.trimIndent(),
            ChatViewModel.Persona.THERAPIST to """
                [Rolle]
                Du bist ein einfuehlsamer Reflexions-Begleiter.
                [Smartes Ziel]
                Foerdere Selbstklarheit, emotionale Stabilisierung und hilfreiche naechste Schritte.
                [Regeln]
                - Aktiv zuhoeren, Kernaussagen spiegeln, respektvoll nachfragen.
                - Validierend und nicht wertend formulieren.
                - Kleine, konkrete und machbare naechste Schritte anbieten.
                [Ausgabestil]
                Ruhig, empathisch, klar strukturiert.
                [Erlaubte Arbeitsweisen]
                Reflexionsfragen, Perspektivwechsel, Selbstregulationsimpulse.
                [Grenzen]
                Du ersetzt keine Psychotherapie oder Krisenintervention.
            """.trimIndent()
        )
    }

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val repo = ChatRepository(ChatDatabase.getDatabase(application).chatDao())
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // ===== State =====
    private val _selectedPersona = MutableStateFlow(loadPersonaFromPrefs())
    val selectedPersona: StateFlow<ChatViewModel.Persona> = _selectedPersona

    private val _customPersonaPrompt = MutableStateFlow(
        prefs.getString("custom_persona_prompt", "") ?: ""
    )
    val customPersonaPrompt: StateFlow<String> = _customPersonaPrompt

    private val _personaAdaptationScores = MutableStateFlow<Map<ChatViewModel.Persona, Int>>(emptyMap())
    val personaAdaptationScores: StateFlow<Map<ChatViewModel.Persona, Int>> = _personaAdaptationScores

    private val _personaMemoryHints = MutableStateFlow<Map<ChatViewModel.Persona, List<String>>>(emptyMap())
    val personaMemoryHints: StateFlow<Map<ChatViewModel.Persona, List<String>>> = _personaMemoryHints

    private val _promptVersions = MutableStateFlow<Map<ChatViewModel.Persona, List<PersonaPromptVersionEntity>>>(emptyMap())
    val promptVersions: StateFlow<Map<ChatViewModel.Persona, List<PersonaPromptVersionEntity>>> = _promptVersions

    private val _personaTrainingExamples = MutableStateFlow<Map<ChatViewModel.Persona, List<Pair<String, String>>>>(emptyMap())
    val personaTrainingExamples: StateFlow<Map<ChatViewModel.Persona, List<Pair<String, String>>>> = _personaTrainingExamples

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // ===== Cache =====
    val systemPromptCache = mutableMapOf<String, Pair<String, Long>>()
    private val PROMPT_CACHE_TTL = 5 * 60 * 1000 // 5 Minuten

    init {
        backfillLocalPromptTimestamps()

        // Lade alle Persona-Daten beim Init
        viewModelScope.launch {
            ChatViewModel.Persona.entries.forEach { persona ->
                syncPersonaDataFromCloud(persona)
                refreshPersonaLearningState(persona)
                refreshPromptVersions(persona)
                refreshPersonaTrainingState(persona)
            }
        }
        
        // Sync bei Login
        if (auth.currentUser != null) {
            viewModelScope.launch {
                syncAllPersonaDataFromCloud()
            }
        }
    }

    // ===== Public API =====

    fun setSelectedPersona(persona: ChatViewModel.Persona) {
        _selectedPersona.value = persona
        prefs.edit().putString("selected_persona", persona.name).apply()
        viewModelScope.launch {
            syncPersonaDataFromCloud(persona)
            refreshPersonaLearningState(persona)
            refreshPromptVersions(persona)
            refreshPersonaTrainingState(persona)
        }
    }

    fun getEditablePromptForPersona(persona: ChatViewModel.Persona): String {
        if (persona == ChatViewModel.Persona.CUSTOM) {
            return _customPersonaPrompt.value.ifBlank { getDefaultPromptForPersona(ChatViewModel.Persona.ASSISTANT) }
        }
        val override = prefs.getString(personaPromptOverrideKey(persona), null)
        return override?.takeIf { it.isNotBlank() } ?: getDefaultPromptForPersona(persona)
    }

    fun getSystemPromptCached(persona: ChatViewModel.Persona): String {
        val key = persona.name
        val cached = systemPromptCache[key]
        if (cached != null && System.currentTimeMillis() - cached.second < PROMPT_CACHE_TTL) {
            return cached.first
        }

        val prompt = buildSystemPrompt(persona)
        systemPromptCache[key] = prompt to System.currentTimeMillis()
        return prompt
    }

    fun setPromptForPersona(persona: ChatViewModel.Persona, prompt: String) {
        val cleaned = prompt.trim()
        if (persona == ChatViewModel.Persona.CUSTOM) {
            setCustomPersonaPrompt(cleaned, source = "manual_edit")
            return
        }

        val previous = prefs.getString(personaPromptOverrideKey(persona), null)?.trim().orEmpty()
        if (cleaned == previous) return

        if (cleaned.isBlank()) {
            prefs.edit().remove(personaPromptOverrideKey(persona)).apply()
        } else {
            prefs.edit().putString(personaPromptOverrideKey(persona), cleaned).apply()
        }
        val updatedAt = System.currentTimeMillis()
        persistPersonaPromptTimestamp(persona, updatedAt)
        maybeSyncPersonaPromptToCloud(persona, cleaned, "manual_edit")

        if (cleaned.isNotBlank()) {
            viewModelScope.launch {
                repo.savePromptVersion(persona.name, cleaned, "manual_edit")
                refreshPromptVersions(persona)
            }
        }

        systemPromptCache.remove(persona.name)
    }

    fun resetPromptForPersona(persona: ChatViewModel.Persona) {
        if (persona == ChatViewModel.Persona.CUSTOM) {
            setCustomPersonaPrompt("", source = "reset")
            return
        }
        prefs.edit().remove(personaPromptOverrideKey(persona)).apply()
        persistPersonaPromptTimestamp(persona, System.currentTimeMillis())
        maybeSyncPersonaPromptToCloud(persona, "", "reset")
        systemPromptCache.remove(persona.name)
    }

    fun addManualTrainingExample(persona: ChatViewModel.Persona, userInput: String, idealResponse: String) {
        if (userInput.isBlank() || idealResponse.isBlank()) {
            _errorMessage.value = "Input und Antwort erforderlich."
            return
        }

        viewModelScope.launch {
            repo.savePersonaTrainingExample(
                personaName = persona.name,
                userInput = userInput,
                idealResponse = idealResponse,
                source = "manual"
            )
            maybeSyncPersonaTrainingExampleToCloud(
                persona = persona,
                userInput = userInput,
                idealResponse = idealResponse,
                source = "manual"
            )
            refreshPersonaTrainingState(persona)
            _errorMessage.value = "Training hinzugefügt für ${persona.displayName}."
        }
    }

    fun rollbackPromptForPersona(persona: ChatViewModel.Persona, versionId: Long) {
        viewModelScope.launch {
            val version = repo.getPromptVersionById(versionId) ?: return@launch
            if (version.personaName != persona.name) return@launch

            if (persona == ChatViewModel.Persona.CUSTOM) {
                setCustomPersonaPrompt(version.promptText, source = "rollback")
            } else {
                prefs.edit().putString(personaPromptOverrideKey(persona), version.promptText).apply()
                persistPersonaPromptTimestamp(persona, System.currentTimeMillis())
                maybeSyncPersonaPromptToCloud(persona, version.promptText, "rollback")
            }

            repo.savePromptVersion(
                personaName = persona.name,
                promptText = version.promptText,
                source = "rollback",
                isRollbackPoint = true
            )

            refreshPromptVersions(persona)
            systemPromptCache.remove(persona.name)
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    // ===== Privat: System-Prompt-Building =====

    private fun buildSystemPrompt(persona: ChatViewModel.Persona): String {
        val personaPrompt = getEditablePromptForPersona(persona)
        val profile = getPersonaProfile(persona)
        val adaptationScore = _personaAdaptationScores.value[persona] ?: 50
        val memories = _personaMemoryHints.value[persona].orEmpty()
        val trainingExamples = _personaTrainingExamples.value[persona].orEmpty()

        return buildString {
            appendLine(personaPrompt)
            appendLine()
            appendLine("[Lernprofil]")
            appendLine("Adaptionsscore: $adaptationScore/100")
            if (memories.isNotEmpty()) {
                appendLine("Feedback-Hinweise:")
                memories.forEach { memory -> appendLine("- $memory") }
            }
            appendLine()
            appendLine("[Charakterprofil]")
            appendLine("Empathie: ${profile.empathy}/100")
            appendLine("Kreativität: ${profile.creativity}/100")
            appendLine("Direktheit: ${profile.directness}/100")
            if (trainingExamples.isNotEmpty()) {
                appendLine()
                appendLine("[Training-Beispiele]")
                trainingExamples.take(2).forEachIndexed { i, (input, output) ->
                    appendLine("Beispiel ${i + 1}:")
                    appendLine("User: $input")
                    appendLine("Ideal: $output")
                }
            }
        }.trim()
    }

    fun getPersonaProfile(persona: ChatViewModel.Persona): PersonaCharacterProfile {
        return PersonaCharacterProfile(
            empathy = prefs.getInt(personaCharacterKey(persona, "empathy"), 50).coerceIn(0, 100),
            creativity = prefs.getInt(personaCharacterKey(persona, "creativity"), 50).coerceIn(0, 100),
            directness = prefs.getInt(personaCharacterKey(persona, "directness"), 50).coerceIn(0, 100)
        )
    }

    // ===== Privat: Cloud-Sync =====

    private suspend fun syncPersonaDataFromCloud(persona: ChatViewModel.Persona) {
        val uid = auth.currentUser?.uid ?: return
        try {
            syncPersonaPromptFromCloud(persona)
            syncPersonaTrainingExamplesFromCloud(persona)
        } catch (e: Exception) {
            AppTelemetry.logError("persona_sync", e)
        }
    }

    private suspend fun syncPersonaPromptFromCloud(persona: ChatViewModel.Persona) {
        val uid = auth.currentUser?.uid ?: return
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("persona_prompts")
            .document(persona.name)
            .get()
            .await()

        if (!snapshot.exists()) return

        val cloudPrompt = snapshot.getString("prompt").orEmpty().trim()
        val cloudUpdatedAt = snapshot.getLong("updatedAt") ?: 0L
        val localPrompt = readLocalStoredPrompt(persona)
        val localUpdatedAt = readLocalPromptUpdatedAt(persona)

        val localIsNewer = localPrompt.isNotBlank() && localUpdatedAt > 0L && localUpdatedAt > cloudUpdatedAt
        if (localIsNewer) {
            maybeSyncPersonaPromptToCloud(persona, localPrompt, "local_newer")
            return
        }

        if (cloudPrompt.isBlank()) {
            val shouldClearLocal = localPrompt.isNotBlank() && cloudUpdatedAt > 0L && cloudUpdatedAt >= localUpdatedAt
            if (shouldClearLocal) {
                if (persona == ChatViewModel.Persona.CUSTOM) {
                    _customPersonaPrompt.value = ""
                    prefs.edit().remove("custom_persona_prompt").apply()
                } else {
                    prefs.edit().remove(personaPromptOverrideKey(persona)).apply()
                }
                persistPersonaPromptTimestamp(persona, cloudUpdatedAt)
                systemPromptCache.remove(persona.name)
            }
            return
        }

        val shouldApplyCloud = localPrompt.isBlank() || (cloudUpdatedAt > 0L && cloudUpdatedAt >= localUpdatedAt)
        if (!shouldApplyCloud) return

        if (persona == ChatViewModel.Persona.CUSTOM) {
            _customPersonaPrompt.value = cloudPrompt
            prefs.edit().putString("custom_persona_prompt", cloudPrompt).apply()
        } else {
            prefs.edit().putString(personaPromptOverrideKey(persona), cloudPrompt).apply()
        }
        persistPersonaPromptTimestamp(persona, if (cloudUpdatedAt > 0L) cloudUpdatedAt else System.currentTimeMillis())
        repo.savePromptVersion(persona.name, cloudPrompt, "cloud_sync")
        refreshPromptVersions(persona)
        systemPromptCache.remove(persona.name)
    }

    private suspend fun syncPersonaTrainingExamplesFromCloud(persona: ChatViewModel.Persona, limit: Int = 30) {
        val uid = auth.currentUser?.uid ?: return
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("persona_training_examples")
            .whereEqualTo("persona", persona.name)
            .limit(limit.toLong())
            .get()
            .await()

        snapshot.documents.forEach { doc ->
            val input = doc.getString("userInput").orEmpty().trim()
            val response = doc.getString("idealResponse").orEmpty().trim()
            if (input.isNotBlank() && response.isNotBlank()) {
                repo.savePersonaTrainingExample(
                    personaName = persona.name,
                    userInput = input,
                    idealResponse = response,
                    source = "cloud_sync"
                )
            }
        }
        refreshPersonaTrainingState(persona)
    }

    private suspend fun syncAllPersonaDataFromCloud() {
        ChatViewModel.Persona.entries.forEach { persona ->
            syncPersonaDataFromCloud(persona)
        }
    }

    private fun maybeSyncPersonaPromptToCloud(persona: ChatViewModel.Persona, prompt: String, source: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users")
                    .document(uid)
                    .collection("persona_prompts")
                    .document(persona.name)
                    .set(
                        mapOf(
                            "persona" to persona.name,
                            "prompt" to prompt,
                            "source" to source,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            } catch (e: Exception) {
                AppTelemetry.logError("persona_prompt_sync", e)
            }
        }
    }

    private fun maybeSyncPersonaTrainingExampleToCloud(
        persona: ChatViewModel.Persona,
        userInput: String,
        idealResponse: String,
        source: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val exampleId = UUID.nameUUIDFromBytes("${persona.name}|$userInput|$idealResponse".toByteArray()).toString()
                firestore.collection("users")
                    .document(uid)
                    .collection("persona_training_examples")
                    .document(exampleId)
                    .set(
                        mapOf(
                            "persona" to persona.name,
                            "userInput" to userInput,
                            "idealResponse" to idealResponse,
                            "source" to source,
                            "createdAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            } catch (e: Exception) {
                AppTelemetry.logError("persona_training_sync", e)
            }
        }
    }

    // ===== Privat: State-Refresh =====

    private suspend fun refreshPersonaLearningState(persona: ChatViewModel.Persona) {
        val score = repo.getPersonaAdaptationScore(persona.name)
        val memories = repo.getRecentPersonaMemory(persona.name, limit = 4)
            .map { it.memoryText }
            .filter { it.isNotBlank() }

        _personaAdaptationScores.value = _personaAdaptationScores.value.toMutableMap().apply {
            put(persona, score)
        }
        _personaMemoryHints.value = _personaMemoryHints.value.toMutableMap().apply {
            put(persona, memories)
        }
    }

    private suspend fun refreshPromptVersions(persona: ChatViewModel.Persona) {
        val versions = repo.getPromptVersions(persona.name, limit = 25)
        _promptVersions.value = _promptVersions.value.toMutableMap().apply {
            put(persona, versions)
        }
    }

    private suspend fun refreshPersonaTrainingState(persona: ChatViewModel.Persona) {
        val examples = repo.getPersonaTrainingExamples(persona.name, limit = 6)
            .map { it.userInput to it.idealResponse }
        _personaTrainingExamples.value = _personaTrainingExamples.value.toMutableMap().apply {
            put(persona, examples)
        }
    }

    // ===== Hilfsfunktionen =====

    private fun loadPersonaFromPrefs(): ChatViewModel.Persona {
        val name = prefs.getString("selected_persona", ChatViewModel.Persona.ASSISTANT.name)
            ?: ChatViewModel.Persona.ASSISTANT.name
        return try {
            ChatViewModel.Persona.valueOf(name)
        } catch (_: Exception) {
            ChatViewModel.Persona.ASSISTANT
        }
    }

    private fun personaPromptOverrideKey(persona: ChatViewModel.Persona): String =
        "persona_prompt_override_${persona.name}"

    private fun personaCharacterKey(persona: ChatViewModel.Persona, trait: String): String =
        "persona_character_${persona.name.lowercase()}_$trait"

    private fun personaPromptUpdatedAtKey(persona: ChatViewModel.Persona): String =
        "persona_prompt_updated_at_${persona.name.lowercase()}"

    private fun persistPersonaPromptTimestamp(persona: ChatViewModel.Persona, updatedAt: Long) {
        prefs.edit().putLong(personaPromptUpdatedAtKey(persona), updatedAt).apply()
    }

    private fun readLocalPromptUpdatedAt(persona: ChatViewModel.Persona): Long =
        prefs.getLong(personaPromptUpdatedAtKey(persona), 0L)

    private fun readLocalStoredPrompt(persona: ChatViewModel.Persona): String {
        return if (persona == ChatViewModel.Persona.CUSTOM) {
            prefs.getString("custom_persona_prompt", "")?.trim().orEmpty()
        } else {
            prefs.getString(personaPromptOverrideKey(persona), "")?.trim().orEmpty()
        }
    }

    private fun setCustomPersonaPrompt(prompt: String, source: String) {
        _customPersonaPrompt.value = prompt
        if (prompt.isBlank()) {
            prefs.edit().remove("custom_persona_prompt").apply()
        } else {
            prefs.edit().putString("custom_persona_prompt", prompt).apply()
        }
        persistPersonaPromptTimestamp(ChatViewModel.Persona.CUSTOM, System.currentTimeMillis())
        maybeSyncPersonaPromptToCloud(ChatViewModel.Persona.CUSTOM, prompt, source)
        if (prompt.isNotBlank()) {
            viewModelScope.launch {
                repo.savePromptVersion(ChatViewModel.Persona.CUSTOM.name, prompt, source)
                refreshPromptVersions(ChatViewModel.Persona.CUSTOM)
            }
        }
        systemPromptCache.remove(ChatViewModel.Persona.CUSTOM.name)
    }

    private fun getDefaultPromptForPersona(persona: ChatViewModel.Persona): String {
        if (persona == ChatViewModel.Persona.CUSTOM) {
            return getDefaultPromptForPersona(ChatViewModel.Persona.ASSISTANT)
        }
        return ENHANCED_DEFAULT_PROMPTS[persona] ?: persona.systemPrompt
    }

    private fun backfillLocalPromptTimestamps() {
        val now = System.currentTimeMillis()
        ChatViewModel.Persona.entries.forEach { persona ->
            val currentTs = readLocalPromptUpdatedAt(persona)
            if (currentTs > 0L) return@forEach
            val localPrompt = readLocalStoredPrompt(persona)
            if (localPrompt.isNotBlank()) {
                persistPersonaPromptTimestamp(persona, now)
            }
        }
    }

    data class PersonaCharacterProfile(
        val empathy: Int = 50,
        val creativity: Int = 50,
        val directness: Int = 50
    )
}
