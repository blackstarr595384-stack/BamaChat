package com.example.bamachat.desktop

import com.example.bamachat.shared.core.ExtensionRuntimeOrchestrator
import com.example.bamachat.shared.core.RuntimeExtension

data class DesktopExtensionPreset(
    val id: String,
    val name: String,
    val description: String,
    val capabilities: Set<String> = emptySet()
) {
    fun toRuntimeExtension(): RuntimeExtension = RuntimeExtension(
        id = id,
        name = name,
        capabilityKeys = capabilities
    )
}

object DesktopExtensionCatalog {
    val all: List<DesktopExtensionPreset> = listOf(
        DesktopExtensionPreset(
            id = ExtensionRuntimeOrchestrator.EXT_RESEARCH_RADAR,
            name = "Research Radar",
            description = "Fuegt evidenzbasierte Recherchehinweise hinzu.",
            capabilities = setOf(ExtensionRuntimeOrchestrator.CAP_LIVE_WEB)
        ),
        DesktopExtensionPreset(
            id = ExtensionRuntimeOrchestrator.EXT_CODE_REVIEW_PRO,
            name = "Code Review Pro",
            description = "Priorisiert Bug-Risiken, Root-Cause und Testbarkeit."
        ),
        DesktopExtensionPreset(
            id = ExtensionRuntimeOrchestrator.EXT_WORKSPACE_ORCHESTRATOR,
            name = "Workspace Orchestrator",
            description = "Erzeugt priorisierte Aufgabenplaene mit Ownern."
        ),
        DesktopExtensionPreset(
            id = ExtensionRuntimeOrchestrator.EXT_COLLAB_FACILITATOR,
            name = "Collab Facilitator",
            description = "Fuegt kollaborative Zusammenfassungen und Next Steps hinzu."
        ),
        DesktopExtensionPreset(
            id = ExtensionRuntimeOrchestrator.EXT_VOICE_BRIEFING,
            name = "Voice Briefing",
            description = "Ergaenzt kurze, vorlesbare Status-Briefings."
        )
    )
}
