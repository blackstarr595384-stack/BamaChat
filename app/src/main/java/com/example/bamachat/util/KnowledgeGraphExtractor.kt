package com.example.bamachat.util

data class KnowledgeEdge(
    val from: String,
    val relation: String,
    val to: String
)

object KnowledgeGraphExtractor {
    private val relationPatterns = listOf(
        Triple(
            Regex("([A-Za-zÄÖÜäöüß0-9\\- ]{2,40})\\s+ist\\s+([A-Za-zÄÖÜäöüß0-9\\- ]{2,60})", RegexOption.IGNORE_CASE),
            "ist",
            2
        ),
        Triple(
            Regex("([A-Za-zÄÖÜäöüß0-9\\- ]{2,40})\\s+führt zu\\s+([A-Za-zÄÖÜäöüß0-9\\- ]{2,60})", RegexOption.IGNORE_CASE),
            "führt_zu",
            2
        ),
        Triple(
            Regex("([A-Za-zÄÖÜäöüß0-9\\- ]{2,40})\\s+verursacht\\s+([A-Za-zÄÖÜäöüß0-9\\- ]{2,60})", RegexOption.IGNORE_CASE),
            "verursacht",
            2
        )
    )

    fun extractEdges(text: String): List<KnowledgeEdge> {
        if (text.isBlank()) return emptyList()
        val edges = mutableListOf<KnowledgeEdge>()
        relationPatterns.forEach { (regex, relation, _) ->
            regex.findAll(text).forEach { match ->
                val from = match.groupValues.getOrNull(1)?.trim().orEmpty()
                val to = match.groupValues.getOrNull(2)?.trim().orEmpty()
                if (from.length >= 2 && to.length >= 2) {
                    edges += KnowledgeEdge(from = from, relation = relation, to = to)
                }
            }
        }
        return edges.distinct().take(12)
    }
}
