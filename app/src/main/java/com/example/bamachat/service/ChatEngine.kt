package com.example.bamachat.service

import android.content.SharedPreferences
import android.app.Application
import android.content.Context
import com.example.bamachat.data.ApiClient
import com.example.bamachat.data.OpenRouterMessage
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.data.model.ChatSource
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.shared.core.ChatSendDeduplicator
import com.example.bamachat.shared.core.ExtensionRuntimeOrchestrator
import com.example.bamachat.shared.core.QuickActionSuggestion
import com.example.bamachat.shared.core.RuntimeExtension
import com.example.bamachat.ui.viewmodel.ApiManager
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.EmotionAnalyzer
import com.example.bamachat.util.EmotionSignal
import com.example.bamachat.util.ExtensionStateStore
import com.example.bamachat.util.MonetizationConfig
import com.example.bamachat.util.MultimodalProcessor
import com.example.bamachat.util.ActiveWorkspaceExtension
import java.util.Locale

class ChatEngine(
    private val apiManager: ApiManager,
    private val app: Application
) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    data class SendResult(
        val accepted: Boolean,
        val userMessage: ChatMessage? = null,
        val emotionSignal: EmotionSignal? = null,
        val error: String? = null
    )

    data class ExtensionRuntime(
        val promptContext: String,
        val appliedExtensionNames: List<String>,
        val forceWebResearch: Boolean
    )

    fun analyzeEmotion(text: String): EmotionSignal = EmotionAnalyzer.analyze(text)

    fun isImageQuery(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("bild", "image", "foto", "photo", "zeichne", "draw",
            "generier", "generat", "erstelle", "create", "male", "paint")
            .any { lower.contains(it) }
    }

    fun isExplicitWebQuery(text: String): Boolean =
        text.trimStart().lowercase(Locale.getDefault()).startsWith("web:")

    fun isDuplicateSend(
        lastNormalizedText: String?,
        lastConversationId: String?,
        lastSentAtMs: Long,
        newNormalizedText: String,
        newConversationId: String?,
        nowMs: Long,
        windowMs: Long = 1300L
    ): Boolean = ChatSendDeduplicator.isDuplicateSend(
        lastNormalizedText, lastConversationId, lastSentAtMs,
        newNormalizedText, newConversationId, nowMs, windowMs
    )

    fun normalizeForDedup(raw: String): String = ChatSendDeduplicator.normalizeForDedup(raw)

    suspend fun buildRuntimeContext(text: String): String? {
        if (!prefs.getBoolean("auto_language_detection_enabled", true)) return null
        val detected = MultimodalProcessor.detectLanguageCode(text) ?: return null
        val appLang = prefs.getString("language", "de")?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (appLang.isBlank() || detected == appLang) return null
        val langName = languageDisplayName(appLang)
        return "Sprach-Kontext: Nutzertext vermutlich in '$detected'. " +
            "Verstehe die Anfrage in dieser Sprache; antworte standardmäßig in $langName " +
            "(Code: $appLang), außer der Nutzer verlangt explizit eine andere Ausgabesprache."
    }

    fun buildExtensionRuntimeContext(
        userText: String,
        quickAction: QuickActionSuggestion,
        activeExtensions: List<ActiveWorkspaceExtension>,
        templateTitles: List<String>
    ): ExtensionRuntime? {
        val runtimeExtensions = activeExtensions.map { ext ->
            RuntimeExtension(
                id = ext.manifest.id,
                name = ext.manifest.name,
                capabilityKeys = ext.grantedCapabilities.map { it.key }.toSet()
            )
        }
        val decision = ExtensionRuntimeOrchestrator.buildRuntimeContext(
            userText = userText,
            quickAction = quickAction,
            activeExtensions = runtimeExtensions,
            templateTitles = templateTitles
        ) ?: return null
        return ExtensionRuntime(
            promptContext = decision.promptContext,
            appliedExtensionNames = decision.appliedExtensionNames,
            forceWebResearch = decision.forceWebResearch
        )
    }

    fun resolveActiveExtensions(rawJson: String?): List<ActiveWorkspaceExtension> =
        ExtensionStateStore.resolveActiveExtensions(rawJson)

    fun buildOpenRouterHistory(
        messages: List<ChatMessage>,
        latestUserText: String? = null,
        liveWebContext: String? = null,
        runtimeContext: String? = null,
        historyLimit: Int = 10
    ): List<OpenRouterMessage> {
        val list = mutableListOf<OpenRouterMessage>()
        val recentMessages = messages.takeLast(historyLimit).toMutableList()

        if (!latestUserText.isNullOrBlank()) {
            val last = recentMessages.lastOrNull()
            if (last == null || !last.isUser || last.text != latestUserText) {
                recentMessages.add(ChatMessage(id = "pending-user", text = latestUserText, isUser = true))
            }
        }

        val lastUserMessageId = recentMessages.lastOrNull { it.isUser }?.id
        recentMessages.forEach { msg ->
            val isLatestUserTurn = msg.isUser && msg.id == lastUserMessageId
            val content = if (isLatestUserTurn) {
                buildString {
                    append(msg.text)
                    if (!runtimeContext.isNullOrBlank()) { append("\n\n"); append(runtimeContext) }
                    if (!liveWebContext.isNullOrBlank()) { append("\n\n"); append(liveWebContext) }
                }
            } else msg.text
            list.add(OpenRouterMessage(role = if (msg.isUser) "user" else "assistant", content = content))
        }
        return list
    }

    suspend fun resolveLiveWebContext(
        text: String,
        forceByExtension: Boolean = false
    ): LiveWebContext? {
        if (!forceByExtension && !apiManager.shouldUseLiveWebResearch(text)) return null
        val cleanedQuery = text.replace("web:", "", ignoreCase = true).trim()
        val research = apiManager.runLiveWebResearch(cleanedQuery)
        if (!research.success) return null

        val sourceRows = research.sources.mapIndexed { index, source ->
            val published = source.publishedAt?.takeIf { it.isNotBlank() }?.let { " (Stand: $it)" } ?: ""
            "${index + 1}. ${source.title}$published\nURL: ${source.url}\nSnippet: ${source.snippet.take(280)}"
        }
        val weatherQuery = isWeatherIntent(text)
        val contextBlock = buildString {
            appendLine("Live-Web-Recherche (aktuell, verifizierbar):")
            appendLine("Query: ${research.query}")
            sourceRows.forEach { appendLine(it); appendLine() }
            if (weatherQuery) {
                appendLine("Wenn die Quellen Wetterdaten enthalten: antworte zuerst konkret mit Ort, Temperatur/Trend und kurzer Empfehlung.")
            }
            appendLine("Nutze diese Quellen nur wenn relevant. Erfinde keine URLs.")
        }.trim()

        return LiveWebContext(
            promptContext = contextBlock,
            sources = research.sources.map {
                ChatSource(title = it.title, url = it.url, snippet = it.snippet, publishedAt = it.publishedAt)
            },
            fetchedAtIso = research.fetchedAtIso
        )
    }

    fun isWeatherIntent(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return listOf("wetter", "temperatur", "regen", "wind", "vorhersage",
            "forecast", "weather", "niederschlag", "gewitter").any { lower.contains(it) }
    }

    fun chargesForWebResearch(): Boolean = prefs.getBoolean("live_web_enabled", false)

    private fun languageDisplayName(code: String): String = when (code.lowercase(Locale.ROOT)) {
        "de" -> "Deutsch"; "en" -> "Englisch"; "fr" -> "Französisch"
        "es" -> "Spanisch"; "pl" -> "Polnisch"; "tr" -> "Türkisch"
        "ar" -> "Arabisch"; else -> "der App-Sprache"
    }

    data class LiveWebContext(
        val promptContext: String,
        val sources: List<ChatSource>,
        val fetchedAtIso: String
    )
}

private typealias MonetizationViewModel = com.example.bamachat.ui.viewmodel.MonetizationViewModel
