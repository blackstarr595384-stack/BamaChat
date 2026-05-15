package com.example.bamachat.shared.core

data class RuntimeExtension(
    val id: String,
    val name: String,
    val capabilityKeys: Set<String> = emptySet()
) {
    fun hasCapability(capabilityKey: String): Boolean = capabilityKeys.contains(capabilityKey)
}

data class ExtensionRuntimeDecision(
    val promptContext: String,
    val appliedExtensionNames: List<String>,
    val forceWebResearch: Boolean
)

object ExtensionRuntimeOrchestrator {
    const val EXT_RESEARCH_RADAR = "ext-research-radar"
    const val EXT_CODE_REVIEW_PRO = "ext-code-review-pro"
    const val EXT_WORKSPACE_ORCHESTRATOR = "ext-workspace-orchestrator"
    const val EXT_COLLAB_FACILITATOR = "ext-collab-facilitator"
    const val EXT_VOICE_BRIEFING = "ext-voice-briefing"
    const val EXT_UI_SIMPLIFIER = "ext-ui-simplifier"
    const val EXT_REPO_AUTOPILOT = "ext-repo-autopilot"
    const val EXT_SYSTEMS_TUNER = "ext-systems-tuner"
    const val CAP_LIVE_WEB = "live_web"

    fun buildRuntimeContext(
        userText: String,
        quickAction: QuickActionSuggestion,
        activeExtensions: List<RuntimeExtension>,
        templateTitles: List<String>
    ): ExtensionRuntimeDecision? {
        val normalized = userText.trim()
        if (normalized.isBlank()) return null

        val templatesLabel = templateTitles.joinToString()
        val hints = mutableListOf<String>()
        val appliedExtensions = linkedSetOf<String>()
        var forceWebResearch = quickAction == QuickActionSuggestion.RESEARCH

        if (quickAction != QuickActionSuggestion.AUTO) {
            appliedExtensions += "Quick: ${quickAction.label()}"
            when (quickAction) {
                QuickActionSuggestion.RESEARCH -> {
                    hints += "Quick Action Research: Antworte evidenzbasiert mit klaren Quellen."
                }
                QuickActionSuggestion.CODE_REVIEW -> {
                    hints += "Quick Action Code Review: Strukturiere als Bugs, Risiken, Fix, Tests."
                }
                QuickActionSuggestion.PLAN -> {
                    hints += "Quick Action Plan: Liefere priorisierte Schritte mit Owner und Deadline."
                }
                QuickActionSuggestion.AUTO -> Unit
            }
        }

        activeExtensions.forEach { extension ->
            when (extension.id) {
                EXT_RESEARCH_RADAR -> {
                    val shouldApply = quickAction == QuickActionSuggestion.RESEARCH ||
                        QuickActionInterpreter.isResearchCentricQuery(normalized)
                    if (shouldApply && extension.hasCapability(CAP_LIVE_WEB)) {
                        forceWebResearch = true
                    }
                    if (shouldApply) {
                        appliedExtensions += extension.name
                        hints += "Research Radar: Gib zuerst Kernaussagen, dann Evidenzpunkte mit Quellen."
                    }
                }
                EXT_CODE_REVIEW_PRO -> {
                    val shouldApply = quickAction == QuickActionSuggestion.CODE_REVIEW ||
                        QuickActionInterpreter.looksLikeCodeRequest(normalized)
                    if (shouldApply) {
                        appliedExtensions += extension.name
                        hints += "Code Review Pro: Priorisiere Bugs, Risiken, Root Cause, Fix und Tests."
                    }
                }
                EXT_WORKSPACE_ORCHESTRATOR -> {
                    val shouldApply = quickAction == QuickActionSuggestion.PLAN ||
                        QuickActionInterpreter.looksLikePlanningRequest(normalized)
                    if (shouldApply) {
                        appliedExtensions += extension.name
                        hints += "Workspace Orchestrator: Liefere eine priorisierte Aktionsliste mit Owner und Deadline. Nutze wenn passend Templates: $templatesLabel."
                    }
                }
                EXT_COLLAB_FACILITATOR -> {
                    val shouldApply = quickAction == QuickActionSuggestion.PLAN ||
                        QuickActionInterpreter.looksLikeCollabRequest(normalized)
                    if (shouldApply) {
                        appliedExtensions += extension.name
                        hints += "Collab Facilitator: Ergänze kurze Zusammenfassung, offene Punkte und klare Next Steps."
                    }
                }
                EXT_VOICE_BRIEFING -> {
                    if (QuickActionInterpreter.looksLikeBriefingRequest(normalized)) {
                        appliedExtensions += extension.name
                        hints += "Voice Briefing: Gib zusätzlich eine kurze, gut vorlesbare 60-90s Zusammenfassung."
                    }
                }
                EXT_UI_SIMPLIFIER -> {
                    val shouldApply = quickAction == QuickActionSuggestion.PLAN ||
                        QuickActionInterpreter.looksLikeOptimizationRequest(normalized) ||
                        QuickActionInterpreter.looksLikePlanningRequest(normalized)
                    if (shouldApply) {
                        appliedExtensions += extension.name
                        hints += "UI Simplifier: Suche nach doppelten Buttons, gleichen Aktionen und unnötigen Parallelwegen. Nutze bei Bedarf `project_inventory`, `ui_action_audit` und `config_audit`, bevor du Änderungen empfiehlst."
                    }
                }
                EXT_REPO_AUTOPILOT -> {
                    val shouldApply = quickAction == QuickActionSuggestion.PLAN ||
                        QuickActionInterpreter.looksLikeOptimizationRequest(normalized) ||
                        QuickActionInterpreter.looksLikeCodeRequest(normalized)
                    if (shouldApply) {
                        appliedExtensions += extension.name
                        hints += "Repo Autopilot: Starte mit `project_inventory`, dann `ui_action_audit` und `config_audit`, bevor du einen schlanken Umsetzungsplan erstellst. Bevorzuge kleine, reversible Schritte und klare Rollout-Checks."
                    }
                }
                EXT_SYSTEMS_TUNER -> {
                    val shouldApply = quickAction == QuickActionSuggestion.PLAN ||
                        QuickActionInterpreter.looksLikeOptimizationRequest(normalized)
                    if (shouldApply) {
                        appliedExtensions += extension.name
                        hints += "Systems Tuner: Prüfe Einstellungen, Defaults, Feature-Flags und doppelte Pfade auf Konflikte. Nutze bei Bedarf `config_audit` und halte die Konfiguration zentral und konsistent."
                    }
                }
            }
        }

        if (activeExtensions.isEmpty() && quickAction == QuickActionSuggestion.AUTO) return null

        val activeNames = activeExtensions.map { it.name }
        val promptContext = buildString {
            if (activeNames.isNotEmpty()) {
                append("Aktive Workspace-Extensions: ${activeNames.joinToString(", ")}.")
            } else {
                append("Keine aktive Workspace-Extension verfügbar; nutze nur Quick-Action-Richtlinien.")
            }
            if (hints.isNotEmpty()) {
                append("\n\n")
                hints.forEach { hint ->
                    append("- ")
                    appendLine(hint)
                }
            }
        }.trim()

        return ExtensionRuntimeDecision(
            promptContext = promptContext,
            appliedExtensionNames = appliedExtensions.toList(),
            forceWebResearch = forceWebResearch
        )
    }

    private fun QuickActionSuggestion.label(): String = when (this) {
        QuickActionSuggestion.AUTO -> "Auto"
        QuickActionSuggestion.RESEARCH -> "Research"
        QuickActionSuggestion.CODE_REVIEW -> "Code Review"
        QuickActionSuggestion.PLAN -> "Plan"
    }
}
