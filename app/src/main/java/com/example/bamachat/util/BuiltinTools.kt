package com.example.bamachat.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object BuiltinTools {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val definitions: List<Map<String, Any>> = listOf(
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "web_fetch",
                "description" to "Ruft eine URL ab und gibt den Inhalt als Text zurück. Nutze dies, um aktuelle Informationen von Webseiten zu holen.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "url" to mapOf("type" to "string", "description" to "Die vollständige URL (inkl. https://)")
                    ),
                    "required" to listOf("url")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "web_search",
                "description" to "Durchsucht das Web nach einer Query und gibt Suchergebnisse zurück.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Die Suchanfrage")
                    ),
                    "required" to listOf("query")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "now",
                "description" to "Gibt das aktuelle Datum und die Uhrzeit zurück.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "format" to mapOf("type" to "string", "description" to "Optional: Datumsformat (z.B. 'dd.MM.yyyy HH:mm')", "default" to "yyyy-MM-dd'T'HH:mm:ss'Z'")
                    )
                )
            )
        )
    )

    suspend fun execute(name: String, args: Map<String, Any>): McpToolResult {
        return withContext(Dispatchers.IO) {
            try {
                when (name) {
                    "web_fetch" -> {
                        val url = args["url"]?.toString() ?: return@withContext error("url fehlt")
                        val request = Request.Builder().url(url).header("User-Agent", "BamaChat/1.0").build()
                        val response = client.newCall(request).execute()
                        val body = response.body?.string() ?: ""
                        val text = if (body.length > 30000) body.take(30000) + "\n\n[truncated]" else body
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = text)))
                    }
                    "web_search" -> {
                        val query = args["query"]?.toString() ?: return@withContext error("query fehlt")
                        val encoded = URLEncoder.encode(query, "UTF-8")
                        val url = "https://html.duckduckgo.com/html/?q=$encoded"
                        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                        val response = client.newCall(request).execute()
                        val html = response.body?.string() ?: ""
                        val results = parseDuckDuckGoResults(html)
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = results)))
                    }
                    "now" -> {
                        val formatStr = args["format"]?.toString() ?: "yyyy-MM-dd'T'HH:mm:ss'Z'"
                        val sdf = SimpleDateFormat(formatStr, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = sdf.format(Date()))))
                    }
                    else -> error("Unbekanntes Builtin-Tool: $name")
                }
            } catch (e: Exception) {
                McpToolResult(success = false, content = listOf(McpContentItem(type = "text", text = "Fehler: ${e.message ?: "Unbekannt"}")), isError = true)
            }
        }
    }

    private fun parseDuckDuckGoResults(html: String): String {
        val sb = StringBuilder()
        val resultRegex = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>([\s\S]*?)</a>""")
        val snippetRegex = Regex("""<a[^>]*class="result__snippet"[^>]*>([\s\S]*?)</a>""")
        val results = resultRegex.findAll(html).toList()
        val snippets = snippetRegex.findAll(html).toList()

        if (results.isEmpty()) return "Keine Ergebnisse gefunden."

        results.forEachIndexed { i, match ->
            val url = match.groupValues[1].replace(Regex("""<[^>]+>"""), "")
            val title = match.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
            val snippet = snippets.getOrNull(i)?.groupValues?.get(1)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: ""
            sb.appendLine("${i + 1}. $title")
            sb.appendLine("   URL: $url")
            if (snippet.isNotBlank()) sb.appendLine("   $snippet")
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    private fun error(msg: String) = McpToolResult(success = false, content = listOf(McpContentItem(type = "text", text = msg)), isError = true)
}
