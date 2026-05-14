package com.example.bamachat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollabWorkspaceMergeTest {

    @Test
    fun mergeWorkspaceTextsInternalUsesLocalWhenRemoteEmpty() {
        val merged = CollabViewModel.mergeWorkspaceTextsInternal(
            remoteText = "",
            localText = "Lokaler Entwurf"
        )
        assertEquals("Lokaler Entwurf", merged)
    }

    @Test
    fun mergeWorkspaceTextsInternalKeepsRemoteAndAppendsLocalOnlyLines() {
        val merged = CollabViewModel.mergeWorkspaceTextsInternal(
            remoteText = "A\nB",
            localText = "A\nC"
        )

        assertTrue(merged.contains("A"))
        assertTrue(merged.contains("B"))
        assertTrue(merged.contains("---- Lokale Ergänzungen ----"))
        assertTrue(merged.contains("C"))
    }

    @Test
    fun buildWorkspaceDiffPreviewInternalReturnsNoDifferencesForEqualText() {
        val preview = CollabViewModel.buildWorkspaceDiffPreviewInternal(
            remoteText = "Alpha\nBeta",
            localText = "Alpha\nBeta"
        )
        assertEquals("Keine Unterschiede.", preview)
    }

    @Test
    fun buildWorkspaceDiffPreviewInternalContainsBothSides() {
        val preview = CollabViewModel.buildWorkspaceDiffPreviewInternal(
            remoteText = "Alpha\nBeta",
            localText = "Alpha\nGamma"
        )
        assertTrue(preview.contains("Lokal exklusiv"))
        assertTrue(preview.contains("Remote exklusiv"))
        assertTrue(preview.contains("Gamma"))
        assertTrue(preview.contains("Beta"))
    }

    @Test
    fun buildWorkspaceDiffDataInternalReturnsStructuredDifferences() {
        val data = CollabViewModel.buildWorkspaceDiffDataInternal(
            remoteText = "Alpha\nBeta",
            localText = "Alpha\nGamma"
        )

        assertFalse(data.identical)
        assertEquals(1, data.sharedCount)
        assertEquals(listOf("Gamma"), data.localOnly)
        assertEquals(listOf("Beta"), data.remoteOnly)
    }

    @Test
    fun buildWorkspaceDiffDataInternalMarksIdenticalTexts() {
        val data = CollabViewModel.buildWorkspaceDiffDataInternal(
            remoteText = "Alpha\nBeta",
            localText = "Alpha\nBeta"
        )

        assertTrue(data.identical)
        assertEquals(0, data.localOnly.size)
        assertEquals(0, data.remoteOnly.size)
    }
}
