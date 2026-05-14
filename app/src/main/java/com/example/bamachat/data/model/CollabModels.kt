package com.example.bamachat.data.model

data class CollabSession(
    val id: String = "",
    val title: String = "",
    val ownerId: String = "",
    val participants: List<String> = emptyList(),
    val participantRoles: Map<String, String> = emptyMap(),
    val inviteCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val aiEnabled: Boolean = true,
    val editorCanUseAi: Boolean = true,
    val editorCanSendMessages: Boolean = true,
    val editorCanEditWorkspace: Boolean = true
)

data class CollabMessage(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isAi: Boolean = false
)

data class CollabPresence(
    val userId: String = "",
    val displayName: String = "",
    val active: Boolean = false,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val typing: Boolean = false,
    val draftPreview: String = "",
    val cursorIndex: Int = 0
)

data class CollabWorkspaceState(
    val text: String = "",
    val updatedBy: String = "",
    val updatedAt: Long = 0L,
    val revision: Long = 0L,
    val baseRevision: Long = 0L
)
