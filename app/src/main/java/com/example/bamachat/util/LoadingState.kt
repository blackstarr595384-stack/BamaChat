package com.example.bamachat.util

/**
 * LoadingState unterscheidet verschiedene Ladezustände für besseres UX.
 * Permet au UI de montrer des messages différents pour chaque état.
 */
sealed class LoadingState {
    data object Idle : LoadingState()
    data object LoadingData : LoadingState()
    data object StreamingAi : LoadingState()
    data class Error(val message: String, val attempts: Int = 1) : LoadingState()
}

/**
 * Integriert Retry-Logik mit exponential backoff in API-Calls.
 * Nutze dies in ChatViewModel.sendChatViaApi() für robustere Fehlerbehandlung.
 */
object ApiCallHelper {
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500,
        block: suspend (attempt: Int) -> T
    ): T {
        var lastError: Exception? = null
        var delayMs = initialDelayMs

        repeat(maxAttempts) { attempt ->
            try {
                return block(attempt + 1)
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts - 1 && ErrorRecoveryManager.shouldRetry(e)) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs = (delayMs * 1.5).toLong().coerceAtMost(5000)
                } else if (attempt == maxAttempts - 1) {
                    throw e
                }
            }
        }

        throw lastError ?: Exception("Unknown error")
    }
}
