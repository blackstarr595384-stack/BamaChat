package com.example.bamachat.shared.core

data class PromptDraft(
    val id: String,
    val text: String,
    val createdAtMs: Long
)

object PromptDrafts {
    fun createOrNull(rawText: String, nowMs: Long = System.currentTimeMillis()): PromptDraft? {
        val cleaned = rawText.trim()
        if (cleaned.isEmpty()) return null
        return PromptDraft(
            id = "${nowMs}-${cleaned.hashCode()}",
            text = cleaned,
            createdAtMs = nowMs
        )
    }

    fun prepend(
        drafts: List<PromptDraft>,
        newDraft: PromptDraft,
        maxItems: Int = 200
    ): List<PromptDraft> {
        if (maxItems <= 0) return listOf(newDraft)
        val merged = buildList {
            add(newDraft)
            drafts.forEach { old ->
                if (old.text != newDraft.text) add(old)
            }
        }
        return if (merged.size <= maxItems) merged else merged.take(maxItems)
    }
}
