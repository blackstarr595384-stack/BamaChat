package com.example.bamachat.shared.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionInterpreterTest {

    @Test
    fun suggestReturnsCodeReviewForCodeRequest() {
        val suggestion = QuickActionInterpreter.suggest("Bitte code review fuer diesen Kotlin Stacktrace")
        assertEquals(QuickActionSuggestion.CODE_REVIEW, suggestion)
    }

    @Test
    fun suggestReturnsPlanForPlanningRequest() {
        val suggestion = QuickActionInterpreter.suggest("Erstelle einen Sprint plan mit milestones")
        assertEquals(QuickActionSuggestion.PLAN, suggestion)
    }

    @Test
    fun suggestReturnsPlanForOptimizationRequest() {
        val suggestion = QuickActionInterpreter.suggest("Bitte optimiere die App und entferne doppelte Buttons")
        assertEquals(QuickActionSuggestion.PLAN, suggestion)
    }

    @Test
    fun researchDetectorFindsResearchTerms() {
        assertTrue(QuickActionInterpreter.isResearchCentricQuery("Was ist die aktuelle version?"))
    }
}
