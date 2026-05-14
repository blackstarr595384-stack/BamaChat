package com.example.bamachat.shared.core

object WorkspaceTextToolkit {
    private val actionMarkers = listOf(
        "todo",
        "to-do",
        "muss",
        "soll",
        "nächste",
        "naechste",
        "deadline",
        "bis",
        "prüfen",
        "pruefen",
        "bauen"
    )

    fun summarize(text: String, maxLines: Int = 5): String {
        val normalized = text.trim()
        if (normalized.isEmpty()) return "Noch keine Inhalte."
        val chunks = normalized
            .split(Regex("[.!?\\n]"))
            .map { it.trim() }
            .filter { it.length > 4 }
            .take(maxLines.coerceAtLeast(1))
        if (chunks.isEmpty()) return normalized.take(240)
        return chunks.joinToString(separator = "\n") { "• $it" }
    }

    fun extractActionItems(text: String, maxItems: Int = 6): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()
        val sentenceCandidates = normalized
            .split(Regex("[\\n.!?]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val prioritized = sentenceCandidates
            .filter { sentence ->
                val lower = sentence.lowercase()
                actionMarkers.any { marker -> lower.contains(marker) }
            }
            .take(maxItems.coerceAtLeast(1))
        return if (prioritized.isNotEmpty()) {
            prioritized
        } else {
            sentenceCandidates.take(maxItems.coerceAtLeast(1))
        }
    }
}
