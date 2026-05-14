package com.example.bamachat.shared.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRuntimeOrchestratorTest {

    @Test
    fun returnsNullForAutoWithoutExtensions() {
        val result = ExtensionRuntimeOrchestrator.buildRuntimeContext(
            userText = "Nur ein lockerer Text",
            quickAction = QuickActionSuggestion.AUTO,
            activeExtensions = emptyList(),
            templateTitles = emptyList()
        )
        assertTrue(result == null)
    }

    @Test
    fun researchRadarCanForceWebResearch() {
        val result = ExtensionRuntimeOrchestrator.buildRuntimeContext(
            userText = "Was ist die aktuelle Version von Kotlin?",
            quickAction = QuickActionSuggestion.AUTO,
            activeExtensions = listOf(
                RuntimeExtension(
                    id = ExtensionRuntimeOrchestrator.EXT_RESEARCH_RADAR,
                    name = "Research Radar",
                    capabilityKeys = setOf(ExtensionRuntimeOrchestrator.CAP_LIVE_WEB)
                )
            ),
            templateTitles = listOf("Release-Check")
        )

        assertNotNull(result)
        assertTrue(result!!.forceWebResearch)
        assertTrue(result.appliedExtensionNames.contains("Research Radar"))
    }

    @Test
    fun explicitCodeReviewQuickActionAddsQuickTag() {
        val result = ExtensionRuntimeOrchestrator.buildRuntimeContext(
            userText = "Bitte reviewe diesen Stacktrace",
            quickAction = QuickActionSuggestion.CODE_REVIEW,
            activeExtensions = emptyList(),
            templateTitles = emptyList()
        )

        assertNotNull(result)
        assertFalse(result!!.appliedExtensionNames.isEmpty())
        assertTrue(result.appliedExtensionNames.first().startsWith("Quick:"))
    }
}
