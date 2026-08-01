package com.example.bamachat.shared.core

import org.junit.Assert.assertFalse
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

        val decision = requireNotNull(result)
        assertTrue(decision.forceWebResearch)
        assertTrue(decision.appliedExtensionNames.contains("Research Radar"))
    }

    @Test
    fun explicitCodeReviewQuickActionAddsQuickTag() {
        val result = ExtensionRuntimeOrchestrator.buildRuntimeContext(
            userText = "Bitte reviewe diesen Stacktrace",
            quickAction = QuickActionSuggestion.CODE_REVIEW,
            activeExtensions = emptyList(),
            templateTitles = emptyList()
        )

        val decision = requireNotNull(result)
        assertFalse(decision.appliedExtensionNames.isEmpty())
        assertTrue(decision.appliedExtensionNames.first().startsWith("Quick:"))
    }

    @Test
    fun repoAutopilotRuntimeHintRemainsReadOnly() {
        val result = ExtensionRuntimeOrchestrator.buildRuntimeContext(
            userText = "Prüfe den Code auf Verbesserungen",
            quickAction = QuickActionSuggestion.CODE_REVIEW,
            activeExtensions = listOf(
                RuntimeExtension(
                    id = ExtensionRuntimeOrchestrator.EXT_REPO_AUTOPILOT,
                    name = "Repo Autopilot",
                    capabilityKeys = setOf(
                        "chat_read",
                        ExtensionRuntimeOrchestrator.CAP_GITHUB_READ
                    )
                )
            ),
            templateTitles = emptyList()
        )

        val decision = requireNotNull(result)
        assertTrue(decision.appliedExtensionNames.contains("Repo Autopilot"))
        assertTrue(decision.promptContext.contains("lesend erfasste Repository-Evidenz"))
        assertTrue(decision.promptContext.contains("keine Änderung automatisch"))
        assertFalse(decision.promptContext.contains("project_inventory"))
        assertFalse(decision.promptContext.contains("run_terminal"))
    }
}
