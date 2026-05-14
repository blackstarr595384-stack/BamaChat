package com.example.bamachat.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceNamingTest {

    @Test
    fun normalizeWorkspaceNameFallsBackToStandard() {
        assertEquals("Standard", WorkspaceNaming.normalizeWorkspaceName("   "))
    }

    @Test
    fun workspaceTagFromTitleExtractsTag() {
        assertEquals("Team Alpha", WorkspaceNaming.workspaceTagFromTitle("[Team Alpha] Neuer Chat"))
    }

    @Test
    fun workspaceTagFromTitleReturnsNullWhenNoTagPresent() {
        assertNull(WorkspaceNaming.workspaceTagFromTitle("Neuer Chat"))
    }

    @Test
    fun placeholderTitleDetectionSupportsTaggedVariant() {
        assertTrue(WorkspaceNaming.isPlaceholderConversationTitle("[Backend] Neuer Chat"))
    }
}
