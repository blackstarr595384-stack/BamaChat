package com.example.bamachat.data.model

data class CollabSession(
    val id: String = "",
    val title: String = "",
    val ownerId: String = "",
    val participants: List<String> = emptyList(),
    val participantRoles: Map<String, String> = emptyMap(),
    val inviteCode: String = "",
    val createdAt: Long = System.currentTimeMillis()
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
    val lastSeenAt: Long = System.currentTimeMillis()
)
