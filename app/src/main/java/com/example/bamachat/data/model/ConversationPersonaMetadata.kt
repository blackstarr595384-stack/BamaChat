package com.example.bamachat.data.model

import java.util.Locale

object ConversationPersonaMetadata {
    const val DEFAULT_PERSONA_DISPLAY_NAME = "Assistent"

    private val messageRoles = setOf("USER", "ASSISTANT", "SYSTEM")
    private val legacyPersonaDisplayNames = mapOf(
        "DEVELOPER" to "Entwickler",
        "TEACHER" to "Lehrer",
        "TRANSLATOR" to "Übersetzer",
        "CHEF" to "Koch",
        "FITNESS" to "Fitness-Coach",
        "THERAPIST" to "Reflexions-Begleiter",
        "CUSTOM" to "Eigene Persona"
    )

    fun resolve(storedPersonaName: String?, activePersonaName: String?): String =
        normalize(storedPersonaName)
            ?: normalize(activePersonaName)
            ?: DEFAULT_PERSONA_DISPLAY_NAME

    private fun normalize(candidate: String?): String? {
        val cleanCandidate = candidate?.trim().orEmpty()
        if (cleanCandidate.isBlank()) return null
        val normalizedKey = cleanCandidate.uppercase(Locale.ROOT)
        if (normalizedKey in messageRoles) return null
        return legacyPersonaDisplayNames[normalizedKey] ?: cleanCandidate
    }
}
