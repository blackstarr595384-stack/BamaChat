package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.RealtimeVoiceEvent
import com.example.bamachat.voice.VoiceFailureCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeEventMapperTest {
    @Test
    fun transcriptEventsMapWithoutPersistingProviderDetails() {
        assertEquals(
            RealtimeVoiceEvent.UserTranscriptDelta("user-item", "Hal"),
            RealtimeEventMapper.map(
                """{"type":"conversation.item.input_audio_transcription.delta","item_id":"user-item","delta":"Hal"}"""
            )
        )
        assertEquals(
            RealtimeVoiceEvent.UserTranscriptCompleted("user-item", "Hallo"),
            RealtimeEventMapper.map(
                """{"type":"conversation.item.input_audio_transcription.completed","item_id":"user-item","transcript":"Hallo"}"""
            )
        )
        assertEquals(
            RealtimeVoiceEvent.AssistantTranscriptDelta("response-1", "assistant-item", "Gu"),
            RealtimeEventMapper.map(
                """{"type":"response.output_audio_transcript.delta","response_id":"response-1","item_id":"assistant-item","delta":"Gu"}"""
            )
        )
        assertEquals(
            RealtimeVoiceEvent.AssistantTranscriptCompleted("response-1", "assistant-item", "Guten Tag"),
            RealtimeEventMapper.map(
                """{"type":"response.output_audio_transcript.done","response_id":"response-1","item_id":"assistant-item","transcript":"Guten Tag"}"""
            )
        )
    }

    @Test
    fun responseDoneIsIdempotentFriendlyAndPreservesCancellation() {
        assertEquals(
            RealtimeVoiceEvent.ResponseCompleted("response-1"),
            RealtimeEventMapper.map(
                """{"type":"response.done","response":{"id":"response-1","status":"completed"}}"""
            )
        )
        assertEquals(
            RealtimeVoiceEvent.ResponseCancelled("response-2"),
            RealtimeEventMapper.map(
                """{"type":"response.done","response":{"id":"response-2","status":"cancelled"}}"""
            )
        )
    }

    @Test
    fun rawProviderErrorIsNeverExposed() {
        val secretProviderMessage = "upstream detail with credential private-marker"
        val event = RealtimeEventMapper.map(
            """{"type":"error","error":{"code":"rate_limit_exceeded","message":"$secretProviderMessage"}}"""
        )

        assertTrue(event is RealtimeVoiceEvent.Failure)
        val failure = (event as RealtimeVoiceEvent.Failure).error
        assertEquals(VoiceFailureCategory.RATE_LIMITED, failure.category)
        assertFalse(failure.userMessage.contains(secretProviderMessage))
        assertFalse(failure.userMessage.contains("private-marker"))
    }

    @Test
    fun malformedUnsafeOrOversizedEventsAreIgnored() {
        assertNull(RealtimeEventMapper.map("not-json"))
        assertNull(
            RealtimeEventMapper.map(
                """{"type":"response.created","response_id":"unsafe id with spaces"}"""
            )
        )
        assertNull(RealtimeEventMapper.map("x".repeat(256_001)))
    }
}
