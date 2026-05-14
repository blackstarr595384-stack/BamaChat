package com.example.bamachat.shared.core

enum class QuickActionSuggestion {
    AUTO,
    RESEARCH,
    CODE_REVIEW,
    PLAN
}

object QuickActionInterpreter {
    private val researchKeywords = listOf(
        "aktuell", "heute", "latest", "news", "update", "release", "version",
        "vergleich", "faktencheck", "quelle", "research", "analyse"
    )

    private val codeKeywords = listOf(
        "code", "kotlin", "java", "python", "swift", "api", "bug",
        "fehler", "exception", "stacktrace", "refactor", "unit test", "gradle"
    )

    private val planningKeywords = listOf(
        "plan", "roadmap", "todo", "to-do", "aufgabe", "nächste schritte",
        "naechste schritte", "sprint", "milestone", "deadline", "release plan"
    )

    private val collabKeywords = listOf(
        "team", "meeting", "collab", "workspace", "stakeholder",
        "session", "abstimmung", "zusammen"
    )

    private val briefingKeywords = listOf(
        "briefing", "zusammenfassung", "summary", "audio", "voice", "sprech"
    )

    fun isResearchCentricQuery(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower.contains("?") || researchKeywords.any { lower.contains(it) }
    }

    fun looksLikeCodeRequest(text: String): Boolean {
        val normalized = text.trim()
        val lower = normalized.lowercase()
        return normalized.contains("```") ||
            normalized.contains("{") ||
            normalized.contains("}") ||
            codeKeywords.any { lower.contains(it) }
    }

    fun looksLikePlanningRequest(text: String): Boolean {
        val lower = text.trim().lowercase()
        return planningKeywords.any { lower.contains(it) }
    }

    fun looksLikeCollabRequest(text: String): Boolean {
        val lower = text.trim().lowercase()
        return collabKeywords.any { lower.contains(it) }
    }

    fun looksLikeBriefingRequest(text: String): Boolean {
        val lower = text.trim().lowercase()
        return briefingKeywords.any { lower.contains(it) }
    }

    fun suggest(text: String): QuickActionSuggestion {
        val normalized = text.trim()
        if (normalized.isEmpty()) return QuickActionSuggestion.AUTO
        return when {
            looksLikeCodeRequest(normalized) -> QuickActionSuggestion.CODE_REVIEW
            looksLikePlanningRequest(normalized) || looksLikeCollabRequest(normalized) ->
                QuickActionSuggestion.PLAN
            isResearchCentricQuery(normalized) -> QuickActionSuggestion.RESEARCH
            else -> QuickActionSuggestion.AUTO
        }
    }
}
