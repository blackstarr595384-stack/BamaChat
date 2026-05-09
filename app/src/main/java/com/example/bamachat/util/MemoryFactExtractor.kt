package com.example.bamachat.util

object MemoryFactExtractor {
    private val patterns = listOf(
        Regex("\\bich hei[ßs]e\\s+([A-Za-zÄÖÜäöüß\\- ]{2,40})", RegexOption.IGNORE_CASE),
        Regex("\\bmein name ist\\s+([A-Za-zÄÖÜäöüß\\- ]{2,40})", RegexOption.IGNORE_CASE),
        Regex("\\bich mag\\s+([^\\.!?\\n]{2,80})", RegexOption.IGNORE_CASE),
        Regex("\\bich liebe\\s+([^\\.!?\\n]{2,80})", RegexOption.IGNORE_CASE),
        Regex("\\bich bevorzuge\\s+([^\\.!?\\n]{2,80})", RegexOption.IGNORE_CASE),
        Regex("\\bmein ziel ist\\s+([^\\.!?\\n]{2,100})", RegexOption.IGNORE_CASE),
        Regex("\\bich arbeite als\\s+([^\\.!?\\n]{2,80})", RegexOption.IGNORE_CASE)
    )

    fun extractFacts(text: String): List<String> {
        val clean = text.trim()
        if (clean.isBlank()) return emptyList()
        val facts = mutableListOf<String>()
        patterns.forEach { regex ->
            regex.findAll(clean).forEach { match ->
                val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (value.length >= 2) {
                    facts += value.replace(Regex("\\s+"), " ")
                }
            }
        }
        return facts.distinct().take(4)
    }
}
