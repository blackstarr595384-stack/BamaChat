package com.example.bamachat.util

import com.google.gson.Gson

enum class ExtensionRiskLevel(val label: String) {
    LOW("Niedrig"),
    MEDIUM("Mittel"),
    HIGH("Hoch")
}

enum class ExtensionCapability(
    val key: String,
    val label: String,
    val description: String,
    val risk: ExtensionRiskLevel
) {
    CHAT_READ(
        key = "chat_read",
        label = "Chat lesen",
        description = "Kann Chatverläufe als Kontext lesen.",
        risk = ExtensionRiskLevel.MEDIUM
    ),
    CHAT_WRITE(
        key = "chat_write",
        label = "Chat schreiben",
        description = "Kann im Namen des Users Nachrichten erstellen.",
        risk = ExtensionRiskLevel.HIGH
    ),
    LIVE_WEB(
        key = "live_web",
        label = "Live-Web",
        description = "Darf Live-Web-Recherche durchführen.",
        risk = ExtensionRiskLevel.MEDIUM
    ),
    FILE_IMPORT(
        key = "file_import",
        label = "Datei-Import",
        description = "Darf lokale Dateien laden und auswerten.",
        risk = ExtensionRiskLevel.MEDIUM
    ),
    WORKSPACE_EDIT(
        key = "workspace_edit",
        label = "Workspace bearbeiten",
        description = "Darf Workspace-Inhalte erstellen und ändern.",
        risk = ExtensionRiskLevel.HIGH
    ),
    COLLAB_CONTROL(
        key = "collab_control",
        label = "Collab steuern",
        description = "Darf Sessions verwalten und Teilnehmer steuern.",
        risk = ExtensionRiskLevel.HIGH
    ),
    VOICE_IO(
        key = "voice_io",
        label = "Voice I/O",
        description = "Darf Sprachaufnahme und Sprachausgabe nutzen.",
        risk = ExtensionRiskLevel.LOW
    ),
    AUTOMATION_RUN(
        key = "automation_run",
        label = "Automationen",
        description = "Darf Vorlagen/Automationen selbst starten.",
        risk = ExtensionRiskLevel.MEDIUM
    );

    companion object {
        fun fromKey(key: String?): ExtensionCapability? {
            val normalized = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == normalized }
        }
    }
}

data class ExtensionManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val category: String,
    val requiredCapabilities: Set<ExtensionCapability> = emptySet(),
    val optionalCapabilities: Set<ExtensionCapability> = emptySet(),
    val experimental: Boolean = false
) {
    val allCapabilities: Set<ExtensionCapability>
        get() = requiredCapabilities + optionalCapabilities
}

data class InstalledExtensionState(
    val extensionId: String,
    val enabled: Boolean = false,
    val installedAt: Long = System.currentTimeMillis(),
    val grantedCapabilities: Set<ExtensionCapability> = emptySet()
) {
    fun missingRequiredCapabilities(manifest: ExtensionManifest): Set<ExtensionCapability> {
        return manifest.requiredCapabilities.filterNot { grantedCapabilities.contains(it) }.toSet()
    }
}

data class ActiveWorkspaceExtension(
    val manifest: ExtensionManifest,
    val installedAt: Long,
    val grantedCapabilities: Set<ExtensionCapability>
) {
    fun hasCapability(capability: ExtensionCapability): Boolean {
        return grantedCapabilities.contains(capability)
    }
}

object ExtensionCatalog {
    val curated: List<ExtensionManifest> = listOf(
        ExtensionManifest(
            id = "ext-research-radar",
            name = "Research Radar",
            description = "Sammelt aktuelle Quellen, priorisiert Evidenz und baut Kurzbriefings.",
            version = "1.0.0",
            author = "Bama Labs",
            category = "Research",
            requiredCapabilities = setOf(
                ExtensionCapability.CHAT_READ,
                ExtensionCapability.LIVE_WEB
            ),
            optionalCapabilities = setOf(ExtensionCapability.AUTOMATION_RUN)
        ),
        ExtensionManifest(
            id = "ext-code-review-pro",
            name = "Code Review Pro",
            description = "Prüft Code-Inputs auf Bugs, Risiken und testbare Fix-Vorschläge.",
            version = "1.0.0",
            author = "Bama Labs",
            category = "Engineering",
            requiredCapabilities = setOf(
                ExtensionCapability.CHAT_READ,
                ExtensionCapability.FILE_IMPORT
            ),
            optionalCapabilities = setOf(
                ExtensionCapability.LIVE_WEB,
                ExtensionCapability.AUTOMATION_RUN
            )
        ),
        ExtensionManifest(
            id = "ext-workspace-orchestrator",
            name = "Workspace Orchestrator",
            description = "Plant Aufgaben, synchronisiert Workspaces und startet Folgeaktionen.",
            version = "1.0.0",
            author = "Bama Labs",
            category = "Productivity",
            requiredCapabilities = setOf(
                ExtensionCapability.WORKSPACE_EDIT,
                ExtensionCapability.AUTOMATION_RUN
            ),
            optionalCapabilities = setOf(
                ExtensionCapability.CHAT_WRITE,
                ExtensionCapability.COLLAB_CONTROL
            ),
            experimental = true
        ),
        ExtensionManifest(
            id = "ext-collab-facilitator",
            name = "Collab Facilitator",
            description = "Moderiert Realtime-Sessions mit Rollen, Zusammenfassungen und ToDos.",
            version = "1.0.0",
            author = "Bama Labs",
            category = "Collaboration",
            requiredCapabilities = setOf(
                ExtensionCapability.CHAT_READ,
                ExtensionCapability.COLLAB_CONTROL
            ),
            optionalCapabilities = setOf(
                ExtensionCapability.WORKSPACE_EDIT,
                ExtensionCapability.CHAT_WRITE
            )
        ),
        ExtensionManifest(
            id = "ext-voice-briefing",
            name = "Voice Briefing",
            description = "Erzeugt gesprochene Status-Updates und strukturiert Voice-Notizen.",
            version = "1.0.0",
            author = "Bama Labs",
            category = "Voice",
            requiredCapabilities = setOf(
                ExtensionCapability.VOICE_IO,
                ExtensionCapability.CHAT_READ
            ),
            optionalCapabilities = setOf(ExtensionCapability.CHAT_WRITE)
        )
    )

    fun findById(extensionId: String): ExtensionManifest? {
        return curated.firstOrNull { it.id == extensionId }
    }
}

object ExtensionStateStore {
    private val gson = Gson()

    private data class PersistedExtensionState(
        val extensionId: String,
        val enabled: Boolean = false,
        val installedAt: Long = 0L,
        val grantedCapabilityKeys: List<String> = emptyList()
    )

    fun encode(states: List<InstalledExtensionState>): String {
        val persisted = states.map { state ->
            PersistedExtensionState(
                extensionId = state.extensionId,
                enabled = state.enabled,
                installedAt = state.installedAt,
                grantedCapabilityKeys = state.grantedCapabilities.map { it.key }
            )
        }
        return gson.toJson(persisted)
    }

    fun decode(raw: String?): List<InstalledExtensionState> {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return emptyList()
        val parsed = runCatching {
            gson.fromJson(normalized, Array<PersistedExtensionState>::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
        return parsed.mapNotNull { state ->
            val id = state.extensionId.trim()
            if (id.isBlank()) return@mapNotNull null
            InstalledExtensionState(
                extensionId = id,
                enabled = state.enabled,
                installedAt = state.installedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                grantedCapabilities = state.grantedCapabilityKeys
                    .mapNotNull { ExtensionCapability.fromKey(it) }
                    .toSet()
            )
        }
    }

    fun resolveActiveExtensions(
        raw: String?,
        catalog: List<ExtensionManifest> = ExtensionCatalog.curated
    ): List<ActiveWorkspaceExtension> {
        return resolveActiveExtensions(decode(raw), catalog)
    }

    fun resolveActiveExtensions(
        states: List<InstalledExtensionState>,
        catalog: List<ExtensionManifest> = ExtensionCatalog.curated
    ): List<ActiveWorkspaceExtension> {
        val manifestsById = catalog.associateBy { it.id }
        return states.asSequence()
            .filter { it.enabled }
            .mapNotNull { state ->
                val manifest = manifestsById[state.extensionId] ?: return@mapNotNull null
                val granted = state.grantedCapabilities.intersect(manifest.allCapabilities)
                val missingRequired = manifest.requiredCapabilities - granted
                if (missingRequired.isNotEmpty()) return@mapNotNull null
                ActiveWorkspaceExtension(
                    manifest = manifest,
                    installedAt = state.installedAt,
                    grantedCapabilities = granted
                )
            }
            .sortedByDescending { it.installedAt }
            .toList()
    }
}
