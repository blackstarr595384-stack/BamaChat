package com.example.bamachat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTextProcessorTest {
    @Test
    fun markdownUrlsCitationsAndCodeAreRemovedBeforeSpeech() {
        val spoken = VoiceTextProcessor.sanitize(
            "# Ergebnis\n**Wichtig:** [Dokument](https://example.com) [1] `inline` " +
                "```kotlin\nprintln(\"secret\")\n``` https://example.com/raw"
        )

        assertTrue(spoken.contains("Ergebnis"))
        assertTrue(spoken.contains("Wichtig:"))
        assertTrue(spoken.contains("Dokument"))
        assertTrue(spoken.contains("inline"))
        assertFalse(spoken.contains("println"))
        assertFalse(spoken.contains("http"))
        assertFalse(spoken.contains("[1]"))
        assertFalse(spoken.contains("*"))
        assertFalse(spoken.contains("`"))
    }

    @Test
    fun streamingChunksStayOrderedAndDoNotRepeat() {
        val buffer = StreamingSpeechBuffer(maxChunkChars = 120)

        val first = buffer.consume("assistant-1", "Erster Satz.", isFinal = false)
        val second = buffer.consume("assistant-1", "Erster Satz. Zweiter Satz.", isFinal = false)
        val final = buffer.consume("assistant-1", "Erster Satz. Zweiter Satz. Schluss", isFinal = true)

        assertEquals(listOf("Erster Satz."), first)
        assertEquals(listOf("Zweiter Satz."), second)
        assertEquals(listOf("Schluss"), final)
    }

    @Test
    fun streamingCodeFenceIsNeverSpokenAcrossDeltas() {
        val buffer = StreamingSpeechBuffer()

        val first = buffer.consume("assistant-1", "Vorher. ``", isFinal = false)
        val second = buffer.consume("assistant-1", "Vorher. ```kotlin\nval token = 1\n", isFinal = false)
        val final = buffer.consume(
            "assistant-1",
            "Vorher. ```kotlin\nval token = 1\n``` Danach.",
            isFinal = true
        )

        assertEquals(listOf("Vorher."), first)
        assertTrue(second.isEmpty())
        assertEquals(listOf("Danach."), final)
    }

    @Test
    fun longTextIsSplitWithoutChangingOrder() {
        val chunks = VoiceTextProcessor.splitCompleteText(
            "Eins ist abgeschlossen. Zwei folgt direkt danach. Drei beendet die geordnete Antwort.",
            maxChunkChars = 35
        )

        assertEquals(
            "Eins ist abgeschlossen. Zwei folgt direkt danach. Drei beendet die geordnete Antwort.",
            chunks.joinToString(" ")
        )
    }
}
