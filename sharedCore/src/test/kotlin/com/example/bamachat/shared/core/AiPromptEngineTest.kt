package com.example.bamachat.shared.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptEngineTest {
    @Test
    fun buildSystemPromptIncludesQuickActionGuidance() {
        val prompt = AiPromptEngine.buildSystemPrompt(
            appName = "BamaChat Desktop",
            quickAction = QuickActionSuggestion.CODE_REVIEW,
            runtimeDecision = null
        )

        assertTrue(prompt.contains("Du bist BamaChat Desktop."))
        assertTrue(prompt.contains("Quick Action: Code Review."))
        assertTrue(prompt.contains("Priorisiere Bugs"))
    }

    @Test
    fun buildSystemPromptIncludesExtensionContextAndResearchHint() {
        val prompt = AiPromptEngine.buildSystemPrompt(
            appName = "BamaChat",
            quickAction = QuickActionSuggestion.AUTO,
            runtimeDecision = ExtensionRuntimeDecision(
                promptContext = "Research Radar aktiv.",
                appliedExtensionNames = listOf("Research Radar"),
                forceWebResearch = true
            )
        )

        assertTrue(prompt.contains("Extension-Kontext:"))
        assertTrue(prompt.contains("Research Radar aktiv."))
        assertTrue(prompt.contains("Quellen der User selbst nachziehen soll"))
    }

    @Test
    fun buildSystemPromptDoesNotAddQuickActionGuidanceForAuto() {
        val prompt = AiPromptEngine.buildSystemPrompt(
            appName = "BamaChat",
            quickAction = QuickActionSuggestion.AUTO,
            runtimeDecision = null
        )

        assertFalse(prompt.contains("Quick Action:"))
    }
}
