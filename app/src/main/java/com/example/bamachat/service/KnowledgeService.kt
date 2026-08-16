package com.example.bamachat.service

import android.app.Application
import android.net.Uri
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.util.KnowledgeGraphExtractor
import com.example.bamachat.util.MemoryFactExtractor
import com.example.bamachat.util.DocumentIngestor
import com.example.bamachat.util.AppTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KnowledgeService(
    private val repo: ChatRepository,
    private val app: Application
) {
    suspend fun extractAndSaveFacts(
        text: String,
        personaName: String,
        sourceMessageId: String,
        confidence: Float = 0.72f
    ) {
        val facts = MemoryFactExtractor.extractFacts(text)
        facts.forEach { fact ->
            repo.saveUserMemoryFact(
                personaName = personaName,
                factText = fact,
                confidence = confidence,
                sourceMessageId = sourceMessageId
            )
        }
    }

    suspend fun extractAndSaveEdges(text: String, ownerScope: String, weight: Float = 0.7f) {
        val edges = KnowledgeGraphExtractor.extractEdges(text)
        edges.forEach { edge ->
            repo.saveKnowledgeEdge(ownerScope, edge.from, edge.relation, edge.to, weight = weight)
        }
    }

    suspend fun importDocument(uri: Uri, ownerScope: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val doc = DocumentIngestor.ingest(app, uri) ?: return@withContext null
                splitIntoChunks(doc.text, 700, 120).take(40).forEach { chunk ->
                    val keywords = extractKeywords(chunk).joinToString(",")
                    repo.saveKnowledgeChunk(
                        ownerScope = ownerScope,
                        sourceTitle = doc.title,
                        content = chunk,
                        keywords = keywords,
                        sourceType = doc.sourceType
                    )
                    KnowledgeGraphExtractor.extractEdges(chunk).forEach { edge ->
                        repo.saveKnowledgeEdge(ownerScope, edge.from, edge.relation, edge.to, weight = 1.0f)
                    }
                }
                AppTelemetry.logEvent("knowledge_import_success", mapOf("source" to doc.sourceType))
                doc.title
            } catch (e: Exception) {
                AppTelemetry.logError("knowledge_import", e)
                null
            }
        }
    }

    suspend fun ingestText(title: String, sourceType: String, text: String, ownerScope: String): Boolean {
        if (text.length < 20) return false
        splitIntoChunks(text, 700, 120).take(50).forEach { chunk ->
            val keywords = extractKeywords(chunk).joinToString(",")
            repo.saveKnowledgeChunk(
                ownerScope = ownerScope,
                sourceTitle = title,
                content = chunk,
                keywords = keywords,
                sourceType = sourceType
            )
            KnowledgeGraphExtractor.extractEdges(chunk).forEach { edge ->
                repo.saveKnowledgeEdge(ownerScope, edge.from, edge.relation, edge.to, weight = 0.9f)
            }
        }
        return true
    }

    suspend fun getEdges(ownerScope: String, limit: Int = 12) = repo.getKnowledgeEdges(ownerScope, limit)

    suspend fun searchKnowledge(token: String, ownerScope: String, limit: Int = 5) =
        repo.searchKnowledge(ownerScope, token, limit)

    suspend fun getFacts(personaName: String, limit: Int = 8) =
        repo.getUserMemoryFacts(personaName, limit)

    suspend fun retrieveRelevantContext(
        query: String,
        personaName: String,
        ownerScope: String,
        maxChunks: Int = 3
    ): String {
        val keywords = extractKeywords(query)
        if (keywords.isEmpty()) return ""

        val chunkResults = keywords.take(3).flatMap { kw ->
            repo.searchKnowledge(ownerScope, kw, limit = maxChunks)
        }.distinctBy { it.id }.take(maxChunks)

        val facts = repo.getUserMemoryFacts(personaName, limit = 5)
            .filter { fact ->
                keywords.any { kw -> fact.factText.contains(kw, ignoreCase = true) }
            }

        val edges = repo.getKnowledgeEdges(ownerScope, limit = 20)
            .filter { edge ->
                keywords.any { kw ->
                    edge.fromConcept.contains(kw, ignoreCase = true) ||
                        edge.toConcept.contains(kw, ignoreCase = true)
                }
            }

        val parts = mutableListOf<String>()

        if (chunkResults.isNotEmpty()) {
            parts += "=== Gespeichertes Wissen ===" +
                chunkResults.joinToString("\n") { "• ${it.content.take(300)}" }
        }
        if (facts.isNotEmpty()) {
            parts += "=== Fakten über Benutzer ===" +
                facts.joinToString("\n") { "• ${it.factText}" }
        }
        if (edges.isNotEmpty()) {
            parts += "=== Wissensnetz (Konzepte & Relationen) ===" +
                edges.joinToString("\n") { "• ${it.fromConcept} → ${it.relation} → ${it.toConcept}" }
        }

        return if (parts.isNotEmpty()) {
            "Wissenskontext aus der lokalen Datenbank (automatisch abgerufen):\n" + parts.joinToString("\n\n")
        } else ""
    }

    private fun splitIntoChunks(text: String, chunkSize: Int, overlap: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= chunkSize) return listOf(clean)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            val end = (start + chunkSize).coerceAtMost(clean.length)
            chunks += clean.substring(start, end).trim()
            if (end == clean.length) break
            start = (end - overlap).coerceAtLeast(0)
        }
        return chunks
    }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            "und", "oder", "aber", "nicht", "mit", "für", "der", "die", "das", "ein", "eine",
            "ist", "sind", "war", "wie", "ich", "du", "wir", "sie", "man", "dass", "wenn"
        )
        return text.lowercase()
            .replace(Regex("[^a-zA-ZäöüÄÖÜß0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 4 && it !in stopWords }
            .distinct()
    }
}
