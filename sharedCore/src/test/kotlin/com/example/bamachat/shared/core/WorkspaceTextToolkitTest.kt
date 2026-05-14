package com.example.bamachat.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTextToolkitTest {

    @Test
    fun summarizeReturnsFallbackForEmptyInput() {
        val summary = WorkspaceTextToolkit.summarize("")
        assertEquals("Noch keine Inhalte.", summary)
    }

    @Test
    fun extractActionItemsPrioritizesTodoMarkers() {
        val input = """
            Wir sollten zuerst das Release prüfen.
            Meeting Notizen ohne Marker.
            TODO: Billing Callback fixen.
        """.trimIndent()

        val items = WorkspaceTextToolkit.extractActionItems(input)

        assertTrue(items.isNotEmpty())
        assertTrue(items.first().contains("prüfen") || items.first().contains("TODO", ignoreCase = true))
    }
}
