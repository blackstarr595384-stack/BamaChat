package com.example.bamachat.service

import com.example.bamachat.data.ApiClient
import com.example.bamachat.shared.core.AiProviderId

enum class AiFailureCategory(val telemetryValue: String) {
    EMPTY_RESPONSE("empty_response"),
    AUTHENTICATION("authentication"),
    RATE_LIMIT("rate_limit"),
    NETWORK("network"),
    PROVIDER_UNAVAILABLE("provider_unavailable")
}

data class UserFacingAiFailure(
    val message: String,
    val category: AiFailureCategory
)

object UserFacingAiErrorMapper {
    fun terminal(provider: AiProviderId, rawError: String?): UserFacingAiFailure {
        val category = category(rawError)
        val providerName = provider.displayName()
        val message = when {
            provider == AiProviderId.OPENROUTER && category == AiFailureCategory.EMPTY_RESPONSE ->
                "OpenRouter hat keine Antwort geliefert. Bitte versuche es erneut oder wähle einen anderen Anbieter."
            category == AiFailureCategory.EMPTY_RESPONSE ->
                "$providerName hat keine Antwort geliefert. Bitte versuche es erneut oder wähle einen anderen Anbieter."
            category == AiFailureCategory.AUTHENTICATION ->
                "$providerName konnte die Anfrage nicht autorisieren. Bitte prüfe die Anbieter-Einstellungen."
            category == AiFailureCategory.RATE_LIMIT ->
                "$providerName ist vorübergehend ausgelastet. Bitte versuche es später erneut."
            category == AiFailureCategory.NETWORK ->
                "$providerName ist wegen eines Netzwerkproblems nicht erreichbar. Bitte prüfe deine Verbindung."
            else ->
                "$providerName hat nicht geantwortet. Bitte versuche es erneut oder wähle einen anderen Anbieter."
        }
        return UserFacingAiFailure(message, category)
    }

    fun fallbackInProgress(provider: ApiClient.Provider): String =
        "${provider.displayName()} antwortet gerade nicht. BamaChat versucht einen anderen Anbieter."

    fun category(rawError: String?): AiFailureCategory {
        val normalized = rawError.orEmpty().trim().lowercase()
        return when {
            normalized.isBlank() || EMPTY_MARKERS.any(normalized::contains) -> AiFailureCategory.EMPTY_RESPONSE
            AUTH_MARKERS.any(normalized::contains) -> AiFailureCategory.AUTHENTICATION
            RATE_LIMIT_MARKERS.any(normalized::contains) -> AiFailureCategory.RATE_LIMIT
            NETWORK_MARKERS.any(normalized::contains) -> AiFailureCategory.NETWORK
            else -> AiFailureCategory.PROVIDER_UNAVAILABLE
        }
    }

    private fun AiProviderId.displayName(): String = when (this) {
        AiProviderId.OPENROUTER -> "OpenRouter"
        AiProviderId.OPENCODE -> "OpenCode"
        AiProviderId.GROQ -> "Groq"
        AiProviderId.CEREBRAS -> "Cerebras"
        AiProviderId.TOGETHER -> "Together"
        AiProviderId.OLLAMA -> "Ollama"
    }

    private fun ApiClient.Provider.displayName(): String = when (this) {
        ApiClient.Provider.OPENROUTER -> "OpenRouter"
        ApiClient.Provider.OPENCODE -> "OpenCode"
        ApiClient.Provider.GROQ -> "Groq"
        ApiClient.Provider.CEREBRAS -> "Cerebras"
        ApiClient.Provider.TOGETHER -> "Together"
    }

    private val EMPTY_MARKERS = listOf(
        "empty response",
        "empty stream",
        "empty_response",
        "empty_stream",
        "no response",
        "leere antwort"
    )
    private val AUTH_MARKERS = listOf("401", "403", "unauthorized", "authentication", "api key")
    private val RATE_LIMIT_MARKERS = listOf("429", "rate limit", "rate_limit", "quota")
    private val NETWORK_MARKERS = listOf("timeout", "timed out", "network", "connection", "offline", "unreachable")
}
