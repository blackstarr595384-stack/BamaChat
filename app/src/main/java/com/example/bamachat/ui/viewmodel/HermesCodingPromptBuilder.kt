package com.example.bamachat.ui.viewmodel

object HermesCodingPromptBuilder {

    data class HermesCodingPrompt(
        val systemPrompt: String,
        val userPrompt: String,
        val preview: String
    )

    private val systemRules = listOf(
        "Du bist der Hermes Coding Assistant für BamaChat.",
        "Führe keine lokalen Befehle aus und fordere keine Befehlsausführung an.",
        "Ändere keine Dateien und behaupte niemals, Dateien geändert zu haben.",
        "Beschränke dich auf Analyse, Code Review und Patch-Vorschläge.",
        "Wenn du einen Patch vorschlägst, kennzeichne ihn ausdrücklich als Vorschlag.",
        "Antworte immer auf Deutsch."
    )

    fun buildPrompt(
        mode: HermesCodingAssistantMode,
        userInput: String
    ): HermesCodingPrompt {
        val trimmedInput = userInput.trim()
        val systemPrompt = buildString {
            systemRules.forEach { rule -> appendLine("- $rule") }
        }.trim()
        val userPrompt = buildString {
            appendLine("MODUS:")
            appendLine(modeInstruction(mode))
            appendLine()
            appendLine("NUTZEREINGABE:")
            appendLine(trimmedInput)
        }.trim()
        val preview = buildString {
            appendLine("SYSTEMREGELN:")
            appendLine(systemPrompt)
            appendLine()
            appendLine("MODUS:")
            appendLine(modeInstruction(mode))
            appendLine()
            appendLine("NUTZEREINGABE:")
            appendLine(trimmedInput)
        }.trim()
        return HermesCodingPrompt(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            preview = preview
        )
    }

    private fun modeInstruction(mode: HermesCodingAssistantMode): String = when (mode) {
        HermesCodingAssistantMode.ANALYSIS ->
            "Analyse: Prüfe den eingegebenen Code oder die Beschreibung auf Fehler, Risiken und Verbesserungsmöglichkeiten. Gib priorisierte Hinweise aus."

        HermesCodingAssistantMode.CODE_REVIEW ->
            "Code Review: Bewerte Lesbarkeit, Wartbarkeit, Architektur, Compose-/Kotlin-Konventionen und mögliche Regressionen. Gib konkrete Review-Punkte aus."

        HermesCodingAssistantMode.PATCH_PROPOSAL ->
            "Patch-Vorschlag: Schlage nur konzeptionelle oder textuelle Änderungen vor. Mache klar, dass nichts angewendet wurde."
    }
}
