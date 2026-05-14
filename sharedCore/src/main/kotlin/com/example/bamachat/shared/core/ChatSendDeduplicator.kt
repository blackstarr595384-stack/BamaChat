package com.example.bamachat.shared.core

object ChatSendDeduplicator {
    private val multiSpaceRegex = Regex("\\s+")

    fun normalizeForDedup(raw: String): String =
        raw.trim().replace(multiSpaceRegex, " ")

    fun isDuplicateSend(
        lastNormalizedText: String?,
        lastConversationId: String?,
        lastSentAtMs: Long,
        newNormalizedText: String,
        newConversationId: String?,
        nowMs: Long,
        windowMs: Long = 1300L
    ): Boolean {
        if (lastNormalizedText.isNullOrEmpty() || lastSentAtMs <= 0L) return false
        if (nowMs - lastSentAtMs > windowMs) return false
        return lastNormalizedText == newNormalizedText && lastConversationId == newConversationId
    }
}
