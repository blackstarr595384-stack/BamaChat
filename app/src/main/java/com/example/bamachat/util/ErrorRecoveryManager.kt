package com.example.bamachat.util

import kotlinx.coroutines.delay
import kotlin.math.pow

data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 100,
    val maxDelayMs: Long = 5000,
    val backoffMultiplier: Double = 2.0
)

sealed class RetryResult<T> {
    data class Success<T>(val value: T) : RetryResult<T>()
    data class Failure<T>(val error: Exception, val attempts: Int) : RetryResult<T>()
}

data class UserErrorMessage(
    val code: String,
    val title: String,
    val description: String,
    val suggestion: String,
    val actionLabel: String? = null,
    val isRetryable: Boolean = false
)

object ErrorRecoveryManager {
    suspend fun <T> withExponentialBackoff(
        config: RetryConfig = RetryConfig(),
        isRetryable: (Exception) -> Boolean = { true },
        block: suspend () -> T
    ): RetryResult<T> {
        var lastException: Exception? = null
        var currentDelayMs = config.initialDelayMs

        repeat(config.maxAttempts) { attempt ->
            try {
                val result = block()
                return RetryResult.Success(result)
            } catch (e: Exception) {
                lastException = e
                if (attempt < config.maxAttempts - 1 && isRetryable(e)) {
                    delay(currentDelayMs)
                    currentDelayMs = (currentDelayMs * config.backoffMultiplier)
                        .toLong()
                        .coerceAtMost(config.maxDelayMs)
                }
            }
        }

        return RetryResult.Failure(lastException ?: Exception("Unknown error"), config.maxAttempts)
    }

    fun isNetworkError(e: Exception): Boolean {
        val message = e.message?.lowercase().orEmpty()
        return message.contains("timeout") ||
            message.contains("unable to resolve host") ||
            message.contains("connection refused") ||
            message.contains("no route to host") ||
            message.contains("network unreachable") ||
            e is java.net.SocketException ||
            e is java.net.ConnectException ||
            e is java.net.UnknownHostException ||
            e is java.io.IOException
    }

    fun isApiError(e: Exception): Boolean {
        val message = e.message?.lowercase().orEmpty()
        if (message.contains("modell nicht gefunden") || message.contains("model not found")) {
            return true
        }
        return message.contains("429") || // Rate limit
            message.contains("404") ||
            message.contains("500") ||
            message.contains("502") ||
            message.contains("503") ||
            message.contains("504")
    }

    fun isModelNotFoundError(e: Exception): Boolean {
        val message = e.message?.lowercase().orEmpty()
        return message.contains("modell nicht gefunden") ||
            message.contains("model not found") ||
            message.contains("404")
    }

    fun isAuthError(e: Exception): Boolean {
        val message = e.message?.lowercase().orEmpty()
        return message.contains("401") ||
            message.contains("403") ||
            message.contains("unauthorized") ||
            message.contains("invalid api key") ||
            message.contains("authentication failed")
    }

    fun isQuotaError(e: Exception): Boolean {
        val message = e.message?.lowercase().orEmpty()
        return message.contains("quota") ||
            message.contains("rate limit") ||
            message.contains("429")
    }

    fun shouldRetry(e: Exception): Boolean {
        return isNetworkError(e) || isApiError(e)
    }

    fun mapErrorToUserMessage(e: Exception): UserErrorMessage {
        return when {
            isAuthError(e) -> UserErrorMessage(
                code = "AUTH_ERROR",
                title = "Authentifizierungsfehler",
                description = "Dein API-Key ist ungültig oder abgelaufen.",
                suggestion = "Überprüfe deine API-Keys in den Einstellungen (KI & Modelle).",
                actionLabel = "Zu Einstellungen",
                isRetryable = false
            )
            isModelNotFoundError(e) -> UserErrorMessage(
                code = "MODEL_NOT_FOUND",
                title = "Modell nicht verfügbar",
                description = "Der aktuelle Provider kennt dieses Modell nicht.",
                suggestion = "Wechsle den Provider oder nutze ein kompatibles Modell in den Einstellungen.",
                actionLabel = "Erneut versuchen",
                isRetryable = true
            )
            isQuotaError(e) -> UserErrorMessage(
                code = "QUOTA_EXCEEDED",
                title = "Quota überschritten",
                description = "Du hast dein tägliches Limit erreicht oder die API hat ein Rate-Limit.",
                suggestion = "Warte einige Minuten und versuche es später erneut.",
                actionLabel = null,
                isRetryable = true
            )
            isNetworkError(e) -> UserErrorMessage(
                code = "NETWORK_ERROR",
                title = "Netzwerkfehler",
                description = "Keine Internetverbindung oder Server nicht erreichbar.",
                suggestion = "Überprüfe deine Internetverbindung und versuche es erneut.",
                actionLabel = "Erneut versuchen",
                isRetryable = true
            )
            isApiError(e) -> UserErrorMessage(
                code = "API_ERROR",
                title = "Server-Fehler",
                description = "Der KI-Provider hat einen Fehler zurückgegeben.",
                suggestion = "Der Provider hat möglicherweise Probleme. Versuche es in einigen Minuten erneut.",
                actionLabel = "Erneut versuchen",
                isRetryable = true
            )
            e.message?.contains("timeout", ignoreCase = true) == true -> UserErrorMessage(
                code = "TIMEOUT",
                title = "Zeitüberschreitung",
                description = "Die Anfrage hat zu lange gedauert.",
                suggestion = "Versuche eine kürzere Nachricht zu senden oder später erneut.",
                actionLabel = "Erneut versuchen",
                isRetryable = true
            )
            else -> UserErrorMessage(
                code = "UNKNOWN_ERROR",
                title = "Unbekannter Fehler",
                description = e.message ?: "Ein unbekannter Fehler ist aufgetreten.",
                suggestion = "Bitte versuche es später erneut. Falls das Problem weiterhin besteht, kontaktiere den Support.",
                actionLabel = "Erneut versuchen",
                isRetryable = true
            )
        }
    }

    suspend fun <T> withCircuitBreaker(
        maxFailures: Int = 5,
        resetTimeMs: Long = 30000,
        block: suspend () -> T
    ): RetryResult<T> {
        var failureCount = 0
        var lastFailureTime = 0L

        try {
            val now = System.currentTimeMillis()
            if (now - lastFailureTime > resetTimeMs) {
                failureCount = 0
            }

            val result = block()
            failureCount = 0
            return RetryResult.Success(result)
        } catch (e: Exception) {
            failureCount++
            lastFailureTime = System.currentTimeMillis()

            if (failureCount >= maxFailures) {
                return RetryResult.Failure(Exception("Circuit breaker open after $maxFailures failures"), failureCount)
            }
            return RetryResult.Failure(e, failureCount)
        }
    }
}
