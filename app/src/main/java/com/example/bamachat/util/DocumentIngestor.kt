package com.example.bamachat.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IngestedDocument(
    val title: String,
    val text: String,
    val sourceType: String
)

object DocumentIngestor {
    suspend fun ingest(context: Context, uri: Uri): IngestedDocument? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryDisplayName(resolver, uri) ?: "Dokument"
        val mime = resolver.getType(uri).orEmpty()
        val sourceType = when {
            mime.contains("text", ignoreCase = true) -> "text"
            mime.contains("markdown", ignoreCase = true) -> "markdown"
            mime.contains("pdf", ignoreCase = true) -> "pdf"
            else -> "unknown"
        }

        val rawText = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val sanitized = rawText.replace(Regex("\\s+"), " ").trim()
        if (sanitized.length < 20) return@withContext null

        IngestedDocument(
            title = name,
            text = sanitized.take(120_000),
            sourceType = sourceType
        )
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && it.moveToFirst()) it.getString(index) else null
            }
        }.getOrNull()
    }
}
