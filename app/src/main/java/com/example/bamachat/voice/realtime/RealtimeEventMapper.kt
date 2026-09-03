package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.RealtimeVoiceEvent
import com.example.bamachat.voice.VoiceFailure
import com.example.bamachat.voice.VoiceFailureCategory
import org.json.JSONObject

object RealtimeEventMapper {
    fun map(rawEvent: String): RealtimeVoiceEvent? {
        if (rawEvent.length > MAX_EVENT_CHARS) return null
        val event = runCatching { JSONObject(rawEvent) }.getOrNull() ?: return null
        return when (event.optString("type")) {
            "session.created", "session.updated" -> RealtimeVoiceEvent.Connected
            "input_audio_buffer.speech_started" -> RealtimeVoiceEvent.SpeechStarted(
                event.safeId("item_id", "itemId")
            )
            "input_audio_buffer.speech_stopped" -> RealtimeVoiceEvent.SpeechStopped
            "conversation.item.input_audio_transcription.delta",
            "input_audio_transcription.delta" -> RealtimeVoiceEvent.UserTranscriptDelta(
                itemId = event.safeId("item_id", "itemId"),
                delta = event.optString("delta").take(MAX_TRANSCRIPT_CHARS)
            )
            "conversation.item.input_audio_transcription.completed",
            "conversation.item.input_audio_transcription.done",
            "input_audio_transcription.completed" -> {
                val itemId = event.safeId("item_id", "itemId") ?: return null
                val transcript = event.firstText("transcript", "text")
                RealtimeVoiceEvent.UserTranscriptCompleted(itemId, transcript)
            }
            "response.created" -> RealtimeVoiceEvent.ResponseCreated(event.responseId() ?: return null)
            "response.output_audio_transcript.delta",
            "response.audio_transcript.delta" -> RealtimeVoiceEvent.AssistantTranscriptDelta(
                responseId = event.responseId() ?: return null,
                itemId = event.safeId("item_id", "itemId"),
                delta = event.optString("delta").take(MAX_TRANSCRIPT_CHARS)
            )
            "response.output_audio_transcript.done",
            "response.output_audio_transcript.completed",
            "response.audio_transcript.done" -> RealtimeVoiceEvent.AssistantTranscriptCompleted(
                responseId = event.responseId() ?: return null,
                itemId = event.safeId("item_id", "itemId"),
                transcript = event.firstText("transcript", "text")
            )
            "response.done" -> {
                val response = event.optJSONObject("response")
                val responseId = event.responseId() ?: return null
                if (response?.optString("status") == "cancelled") {
                    RealtimeVoiceEvent.ResponseCancelled(responseId)
                } else {
                    RealtimeVoiceEvent.ResponseCompleted(responseId)
                }
            }
            "response.cancelled" -> RealtimeVoiceEvent.ResponseCancelled(event.responseId() ?: return null)
            "error" -> RealtimeVoiceEvent.Failure(mapSafeError(event.optJSONObject("error") ?: event))
            "session.closed", "connection.closed" -> RealtimeVoiceEvent.Closed
            else -> null
        }
    }

    private fun mapSafeError(error: JSONObject): VoiceFailure {
        val code = error.optString("code").lowercase()
        val category = when {
            "rate" in code -> VoiceFailureCategory.RATE_LIMITED
            "auth" in code || "token" in code -> VoiceFailureCategory.AUTHENTICATION_REQUIRED
            "timeout" in code -> VoiceFailureCategory.TIMEOUT
            else -> VoiceFailureCategory.TEMPORARY_SERVICE_ERROR
        }
        val message = when (category) {
            VoiceFailureCategory.RATE_LIMITED -> "OpenAI Realtime ist ausgelastet. Bitte versuche es später erneut."
            VoiceFailureCategory.AUTHENTICATION_REQUIRED -> "Die Live-Berechtigung ist abgelaufen. Bitte starte die Sitzung neu."
            VoiceFailureCategory.TIMEOUT -> "Die Live-Verbindung hat zu lange nicht geantwortet."
            else -> "Die Live-Unterhaltung wurde vorübergehend unterbrochen."
        }
        return VoiceFailure(category, message)
    }

    private fun JSONObject.responseId(): String? =
        safeId("response_id", "responseId")
            ?: optJSONObject("response")?.safeId("id")

    private fun JSONObject.safeId(vararg keys: String): String? = keys
        .asSequence()
        .map { optString(it).trim() }
        .firstOrNull { it.length in 1..MAX_ID_CHARS && SAFE_ID.matches(it) }

    private fun JSONObject.firstText(vararg keys: String): String = keys
        .asSequence()
        .map { optString(it).trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .take(MAX_TRANSCRIPT_CHARS)

    private const val MAX_EVENT_CHARS = 256_000
    private const val MAX_TRANSCRIPT_CHARS = 32_000
    private const val MAX_ID_CHARS = 160
    private val SAFE_ID = Regex("[A-Za-z0-9_.:-]+")
}
