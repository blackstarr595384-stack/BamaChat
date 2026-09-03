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

    @Test
    fun githubReadCapabilityMapsFromStableKey() {
        assertEquals(
            ExtensionCapability.GITHUB_READ,
            ExtensionCapability.fromKey("github_read")
        )
        assertEquals(
            ExtensionCapability.GITHUB_READ,
            ExtensionCapability.fromKey(" GITHUB_READ ")
        )
    }

    @Test
    fun existingCapabilityKeysRemainUnchanged() {
        assertEquals("chat_read", ExtensionCapability.CHAT_READ.key)
        assertEquals("chat_write", ExtensionCapability.CHAT_WRITE.key)
        assertEquals("live_web", ExtensionCapability.LIVE_WEB.key)
        assertEquals("file_import", ExtensionCapability.FILE_IMPORT.key)
        assertEquals("workspace_edit", ExtensionCapability.WORKSPACE_EDIT.key)
        assertEquals("collab_control", ExtensionCapability.COLLAB_CONTROL.key)
        assertEquals("voice_io", ExtensionCapability.VOICE_IO.key)
        assertEquals("automation_run", ExtensionCapability.AUTOMATION_RUN.key)
    }

    @Test
    fun repoAutopilotRequiresOnlyReadCapabilities() {
        val manifest = requireNotNull(ExtensionCatalog.findById("ext-repo-autopilot"))

        assertEquals("1.1.0", manifest.version)
        assertEquals(
            setOf(ExtensionCapability.CHAT_READ, ExtensionCapability.GITHUB_READ),
            manifest.requiredCapabilities
        )
        assertEquals(
            setOf(ExtensionCapability.FILE_IMPORT, ExtensionCapability.LIVE_WEB),
            manifest.optionalCapabilities
        )
        assertFalse(manifest.allCapabilities.contains(ExtensionCapability.WORKSPACE_EDIT))
        assertFalse(manifest.allCapabilities.contains(ExtensionCapability.AUTOMATION_RUN))
    }

    @Test
    fun installingRepoAutopilotDoesNotGrantCapabilitiesAutomatically() {
        val installed = InstalledExtensionState(extensionId = "ext-repo-autopilot")
        val manifest = requireNotNull(ExtensionCatalog.findById("ext-repo-autopilot"))

        assertTrue(installed.grantedCapabilities.isEmpty())
        assertEquals(manifest.requiredCapabilities, installed.missingRequiredCapabilities(manifest))
        assertTrue(ExtensionStateStore.resolveActiveExtensions(listOf(installed)).isEmpty())
    }

    @Test
    fun unknownPersistedCapabilityKeysRemainSafeAndReadable() {
        val decoded = ExtensionStateStore.decode(
            """
                [{
                  "extensionId":"ext-repo-autopilot",
                  "enabled":true,
                  "installedAt":1234,
                  "grantedCapabilityKeys":["chat_read","future_capability"]
                }]
            """.trimIndent()
        )

        assertEquals(1, decoded.size)
        assertEquals(setOf(ExtensionCapability.CHAT_READ), decoded.single().grantedCapabilities)
        assertTrue(ExtensionStateStore.resolveActiveExtensions(decoded).isEmpty())
    }
}
