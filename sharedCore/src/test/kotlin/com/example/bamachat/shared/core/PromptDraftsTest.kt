package com.example.bamachat.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PromptDraftsTest {

    @Test
    fun createOrNullRejectsBlankInput() {
        assertNull(PromptDrafts.createOrNull("   "))
    }

    @Test
    fun prependPlacesNewestDraftFirstAndRemovesDuplicateText() {
        val first = PromptDraft(id = "1", text = "Alpha", createdAtMs = 1L)
        val second = PromptDraft(id = "2", text = "Beta", createdAtMs = 2L)
        val duplicate = PromptDraft(id = "3", text = "Alpha", createdAtMs = 3L)

        val merged = PromptDrafts.prepend(listOf(first, second), duplicate, maxItems = 10)

        assertNotNull(merged.firstOrNull())
        assertEquals("Alpha", merged.first().text)
        assertEquals("Beta", merged.last().text)
        assertEquals(2, merged.size)
    }
}
