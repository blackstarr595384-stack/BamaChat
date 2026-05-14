package com.example.bamachat.shared.core

object WorkspaceNaming {
    private val workspaceTitleTagRegex = Regex("^\\[([^\\]]+)]\\s*")

    fun normalizeWorkspaceName(raw: String): String =
        raw.trim().ifBlank { "Standard" }

    fun workspaceTagFromTitle(title: String): String? {
        val match = workspaceTitleTagRegex.find(title.trim()) ?: return null
        return normalizeWorkspaceName(match.groupValues.getOrNull(1).orEmpty())
    }

    fun isPlaceholderConversationTitle(
        title: String,
        placeholderTitle: String = "Neuer Chat"
    ): Boolean {
        return title == placeholderTitle || title.endsWith("] $placeholderTitle")
    }
}
