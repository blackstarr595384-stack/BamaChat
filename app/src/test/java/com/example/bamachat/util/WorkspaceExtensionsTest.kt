package com.example.bamachat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceExtensionsTest {

    @Test
    fun decodeReturnsEmptyForBlankInput() {
        assertTrue(ExtensionStateStore.decode(null).isEmpty())
        assertTrue(ExtensionStateStore.decode("   ").isEmpty())
    }

    @Test
    fun encodeDecodeRoundTripPreservesCapabilities() {
        val source = listOf(
            InstalledExtensionState(
                extensionId = "ext-research-radar",
                enabled = true,
                installedAt = 1234L,
                grantedCapabilities = setOf(
                    ExtensionCapability.CHAT_READ,
                    ExtensionCapability.LIVE_WEB
                )
            )
        )

        val encoded = ExtensionStateStore.encode(source)
        val decoded = ExtensionStateStore.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals("ext-research-radar", decoded.first().extensionId)
        assertTrue(decoded.first().grantedCapabilities.contains(ExtensionCapability.CHAT_READ))
        assertTrue(decoded.first().grantedCapabilities.contains(ExtensionCapability.LIVE_WEB))
    }

    @Test
    fun missingRequiredCapabilitiesIsCalculatedCorrectly() {
        val manifest = ExtensionManifest(
            id = "test",
            name = "Test",
            description = "Test",
            version = "1",
            author = "QA",
            category = "Test",
            requiredCapabilities = setOf(
                ExtensionCapability.CHAT_READ,
                ExtensionCapability.CHAT_WRITE
            )
        )

        val state = InstalledExtensionState(
            extensionId = "test",
            grantedCapabilities = setOf(ExtensionCapability.CHAT_READ)
        )
        val missing = state.missingRequiredCapabilities(manifest)

        assertEquals(1, missing.size)
        assertTrue(missing.contains(ExtensionCapability.CHAT_WRITE))
    }

    @Test
    fun resolveActiveExtensionsFiltersDisabledAndInvalidStates() {
        val states = listOf(
            InstalledExtensionState(
                extensionId = "ext-research-radar",
                enabled = true,
                grantedCapabilities = setOf(
                    ExtensionCapability.CHAT_READ,
                    ExtensionCapability.LIVE_WEB
                )
            ),
            InstalledExtensionState(
                extensionId = "ext-code-review-pro",
                enabled = false,
                grantedCapabilities = setOf(
                    ExtensionCapability.CHAT_READ,
                    ExtensionCapability.FILE_IMPORT
                )
            ),
            InstalledExtensionState(
                extensionId = "ext-collab-facilitator",
                enabled = true,
                grantedCapabilities = setOf(ExtensionCapability.CHAT_READ)
            )
        )

        val active = ExtensionStateStore.resolveActiveExtensions(states)

        assertEquals(1, active.size)
        assertEquals("ext-research-radar", active.first().manifest.id)
        assertTrue(active.first().hasCapability(ExtensionCapability.LIVE_WEB))
        assertFalse(active.any { it.manifest.id == "ext-code-review-pro" })
        assertFalse(active.any { it.manifest.id == "ext-collab-facilitator" })
    }
}
