package com.example.bamachat.voice

internal class RealtimeTurnAccumulator(
    private val nowMillis: () -> Long
) {
    private val finalizedUserItemIds = linkedSetOf<String>()
    private val finalizedResponseIds = linkedSetOf<String>()
    private val assistantTranscripts = mutableMapOf<String, String>()

    fun finalizeUser(itemId: String, transcript: String): RealtimeFinalizedTurn? {
        val cleanId = itemId.trim()
        val cleanTranscript = transcript.trim()
        if (cleanId.isBlank() || cleanTranscript.isBlank() || !finalizedUserItemIds.add(cleanId)) {
            return null
        }
        return RealtimeFinalizedTurn(
            messageId = "rt-user-$cleanId",
            text = cleanTranscript,
            isUser = true,
            timestamp = nowMillis()
        )
    }

    fun beginAssistant(responseId: String) {
        val cleanId = responseId.trim()
        if (cleanId.isNotBlank() && cleanId !in finalizedResponseIds) {
            assistantTranscripts.putIfAbsent(cleanId, "")
        }
    }

    fun appendAssistant(responseId: String, delta: String): String? {
        val cleanId = responseId.trim()
        if (cleanId.isBlank() || cleanId in finalizedResponseIds || delta.isBlank()) return null
        val updated = (assistantTranscripts[cleanId].orEmpty() + delta)
            .take(MAX_REALTIME_TRANSCRIPT_CHARS)
        assistantTranscripts[cleanId] = updated
        return updated
    }

    fun completeAssistantTranscript(responseId: String, transcript: String): String? {
        val cleanId = responseId.trim()
        val cleanTranscript = transcript.trim().take(MAX_REALTIME_TRANSCRIPT_CHARS)
        if (cleanId.isBlank() || cleanId in finalizedResponseIds || cleanTranscript.isBlank()) return null
        assistantTranscripts[cleanId] = cleanTranscript
        return cleanTranscript
    }

    fun finalizeAssistant(responseId: String): RealtimeFinalizedTurn? {
        val cleanId = responseId.trim()
        if (cleanId.isBlank() || !finalizedResponseIds.add(cleanId)) return null
        val transcript = assistantTranscripts.remove(cleanId).orEmpty().trim()
        if (transcript.isBlank()) return null
        return RealtimeFinalizedTurn(
            messageId = "rt-assistant-$cleanId",
            text = transcript,
            isUser = false,
            timestamp = nowMillis()
        )
    }

    fun cancelAssistant(responseId: String) {
        val cleanId = responseId.trim()
        if (cleanId.isBlank()) return
        finalizedResponseIds.add(cleanId)
        assistantTranscripts.remove(cleanId)
    }

    fun reset() {
        finalizedUserItemIds.clear()
        finalizedResponseIds.clear()
        assistantTranscripts.clear()
    }

    companion object {
        private const val MAX_REALTIME_TRANSCRIPT_CHARS = 32_000
    }
}
