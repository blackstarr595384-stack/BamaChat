package com.example.bamachat.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "read_file",
                "description" to "Liest eine Datei aus dem App-Sandbox-Speicher und gibt den Inhalt als Text zurück.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Relativer Pfad zur Datei (z.B. 'notes/todo.txt')")
                    ),
                    "required" to listOf("path")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "write_file",
                "description" to "Schreibt Inhalt in eine Datei im App-Sandbox (erzeugt Unterverzeichnisse bei Bedarf).",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Relativer Pfad zur Datei (z.B. 'notes/todo.txt')"),
                        "content" to mapOf("type" to "string", "description" to "Der zu schreibende Inhalt")
                    ),
                    "required" to listOf("path", "content")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "edit_file",
                "description" to "Ersetzt einen Textabschnitt in einer Datei. Nutze dies, um gezielte Änderungen vorzunehmen.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Relativer Pfad zur Datei (z.B. 'notes/todo.txt')"),
                        "oldString" to mapOf("type" to "string", "description" to "Der zu ersetzende Text (exakt)"),
                        "newString" to mapOf("type" to "string", "description" to "Der neue Text")
                    ),
                    "required" to listOf("path", "oldString", "newString")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "run_terminal",
                "description" to "Führt einen Shell-Befehl aus (Sandbox). Nutze dies für git, ls, mkdir, grep, find, node, python usw. Gefährliche Befehle (rm -rf, dd, sudo) sind blockiert.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "command" to mapOf("type" to "string", "description" to "Der auszuführende Shell-Befehl"),
                        "timeoutSeconds" to mapOf("type" to "number", "description" to "Timeout in Sekunden (Standard: 30)")
                    ),
                    "required" to listOf("command")
                )
            )
        )
    )

    suspend fun execute(name: String, args: Map<String, Any>, basePath: String = ""): McpToolResult {
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
                    "read_file" -> {
                        val path = args["path"]?.toString() ?: return@withContext error("path fehlt")
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val file = sandboxFile(basePath, path)
                        if (!file.exists()) return@withContext error("Datei nicht gefunden: $path")
                        val text = file.readText()
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = text)))
                    }
                    "write_file" -> {
                        val path = args["path"]?.toString() ?: return@withContext error("path fehlt")
                        val content = args["content"]?.toString() ?: return@withContext error("content fehlt")
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val file = sandboxFile(basePath, path)
                        file.parentFile?.mkdirs()
                        file.writeText(content)
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = "Datei geschrieben: $path (${content.length} Zeichen)")))
                    }
                    "edit_file" -> {
                        val path = args["path"]?.toString() ?: return@withContext error("path fehlt")
                        val oldString = args["oldString"]?.toString() ?: return@withContext error("oldString fehlt")
                        val newString = args["newString"]?.toString() ?: return@withContext error("newString fehlt")
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val file = sandboxFile(basePath, path)
                        if (!file.exists()) return@withContext error("Datei nicht gefunden: $path")
                        val original = file.readText()
                        if (!original.contains(oldString)) return@withContext error("oldString nicht gefunden in $path")
                        val result = original.replace(oldString, newString)
                        file.writeText(result)
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = "Datei bearbeitet: $path (${result.length - original.length} Zeichen Differenz)")))
                    }
                    "run_terminal" -> {
                        val command = args["command"]?.toString() ?: return@withContext error("command fehlt")
                        val timeout = (args["timeoutSeconds"] as? Number)?.toLong() ?: 30L
                        val blocked = listOf(
                            "rm -rf /", "rm -rf /*", "dd if=", "mkfs.", "shutdown", "reboot",
                            "sudo ", "chmod 777", ">:"
                        )
                        if (blocked.any { command.contains(it, ignoreCase = true) })
                            return@withContext error("Dieser Befehl ist aus Sicherheitsgründen blockiert")
                        val proc = ProcessBuilder("/system/bin/sh", "-c", command)
                            .directory(if (basePath.isNotBlank()) File(basePath) else null)
                            .redirectErrorStream(true)
                            .start()
                        val output = if (proc.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS)) {
                            proc.inputStream.bufferedReader().readText()
                        } else {
                            proc.destroyForcibly()
                            "Timout nach ${timeout}s"
                        }
                        val exitCode = proc.exitValue()
                        val truncated = if (output.length > 10000) output.take(10000) + "\n\n[truncated]" else output
                        McpToolResult(
                            success = exitCode == 0,
                            content = listOf(McpContentItem(type = "text", text = "Exit $exitCode:\n$truncated"))
                        )
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

    private fun sandboxFile(basePath: String, relativePath: String): File {
        val resolved = File(basePath, relativePath).normalize().absoluteFile
        val base = File(basePath).normalize().absoluteFile
        if (!resolved.absolutePath.startsWith(base.absolutePath + File.separator) && resolved != base) {
            throw SecurityException("Pfad außerhalb des Sandboxes: $relativePath")
        }
        return resolved
    }

    private fun error(msg: String) = McpToolResult(success = false, content = listOf(McpContentItem(type = "text", text = msg)), isError = true)
}
