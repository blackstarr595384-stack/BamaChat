package com.example.bamachat.util

import com.google.gson.Gson

data class ProjectWorkspace(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object ProjectWorkspaceStore {
    private val gson = Gson()

    fun defaultWorkspaces(): List<ProjectWorkspace> = listOf(
        ProjectWorkspace(
            id = "ws-default",
            name = "Standard",
            description = "Allgemeiner Workspace"
        ),
        ProjectWorkspace(
            id = "ws-dev",
            name = "Entwicklung",
            description = "Code, Debugging, Releases"
        )
    )

    fun encode(items: List<ProjectWorkspace>): String {
        return gson.toJson(items)
    }

    fun decode(raw: String?): List<ProjectWorkspace> {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return defaultWorkspaces()
        return runCatching {
            gson.fromJson(normalized, Array<ProjectWorkspace>::class.java)
                ?.toList()
                .orEmpty()
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }.getOrDefault(defaultWorkspaces())
            .ifEmpty { defaultWorkspaces() }
    }
}

data class AutomationTemplate(
    val id: String,
    val title: String,
    val description: String,
    val prompt: String
)

object AutomationCatalog {
    val templates: List<AutomationTemplate> = listOf(
        AutomationTemplate(
            id = "daily-briefing",
            title = "Tagesbriefing",
            description = "Wichtige Punkte + Prioritäten",
            prompt = "Erstelle ein kurzes Tagesbriefing mit Top-3 Prioritäten und Risiken."
        ),
        AutomationTemplate(
            id = "meeting-to-tasks",
            title = "Meeting -> ToDos",
            description = "Notizen in Aufgaben umwandeln",
            prompt = "Extrahiere ToDos, Verantwortliche und Deadlines aus dem folgenden Text."
        ),
        AutomationTemplate(
            id = "release-check",
            title = "Release Checkliste",
            description = "Technischer Release-Readiness Check",
            prompt = "Erstelle eine Release-Checkliste mit Test, Security, Monitoring und Rollback."
        ),
        AutomationTemplate(
            id = "risk-scan",
            title = "Risiko-Scan",
            description = "Schwachstellen schnell erkennen",
            prompt = "Analysiere den Text auf Risiken, Impact und Gegenmaßnahmen."
        )
    )
}
