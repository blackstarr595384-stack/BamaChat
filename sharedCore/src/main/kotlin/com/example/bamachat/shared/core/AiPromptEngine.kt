package com.example.bamachat.shared.core

object AiPromptEngine {
    fun buildSystemPrompt(
        appName: String,
        quickAction: QuickActionSuggestion,
        runtimeDecision: ExtensionRuntimeDecision?
    ): String {
        val lines = mutableListOf(
            "Du bist $appName.",
            "Antworte klar, direkt und in der Sprache der letzten Nutzernachricht.",
            "Strukturiere Ergebnisse so, dass sie direkt umsetzbar sind."
        )
        when (quickAction) {
            QuickActionSuggestion.RESEARCH -> {
                lines += "Quick Action: Research. Liefere belastbare Aussagen und nenne Unsicherheiten klar."
            }
            QuickActionSuggestion.CODE_REVIEW -> {
                lines += "Quick Action: Code Review. Priorisiere Bugs, Risiken, Fixes und Tests."
            }
            QuickActionSuggestion.PLAN -> {
                lines += "Quick Action: Plan. Gib priorisierte Schritte mit Verantwortlichkeit und Reihenfolge."
            }
            QuickActionSuggestion.AUTO -> Unit
        }
        runtimeDecision?.let { decision ->
            lines += "Extension-Kontext:"
            lines += decision.promptContext
            if (decision.forceWebResearch) {
                lines += "Hinweis: Wenn aktuelle Fakten fehlen, explizit sagen, welche Quellen der User selbst nachziehen soll."
            }
        }
        return lines.joinToString("\n")
    }
}
