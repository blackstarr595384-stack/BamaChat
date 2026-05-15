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
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "list_files",
                "description" to "Listet Dateien und Ordner in einem Verzeichnis im App-Sandbox auf.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Relativer Pfad zum Ordner (z.B. '.' für Wurzel, 'notes' für Unterordner)"),
                        "recursive" to mapOf("type" to "boolean", "description" to "Optional: Unterordner rekursiv durchsuchen", "default" to false)
                    ),
                    "required" to listOf("path")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "search_files",
                "description" to "Sucht im App-Sandbox-Pfad nach Dateinamen oder Dateiinhalt und gibt Treffer mit kurzen Snippets zurück.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Relativer Startpfad (z.B. '.' oder 'notes')"),
                        "query" to mapOf("type" to "string", "description" to "Suchtext"),
                        "recursive" to mapOf("type" to "boolean", "description" to "Unterordner durchsuchen", "default" to true),
                        "searchContents" to mapOf("type" to "boolean", "description" to "Auch den Dateiinhalt durchsuchen", "default" to true),
                        "maxResults" to mapOf("type" to "number", "description" to "Maximale Anzahl an Treffern", "default" to 50)
                    ),
                    "required" to listOf("path", "query")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "copy_file",
                "description" to "Kopiert eine Datei oder ein Verzeichnis innerhalb des App-Sandboxes an einen neuen Ort.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "sourcePath" to mapOf("type" to "string", "description" to "Relativer Quellpfad"),
                        "destinationPath" to mapOf("type" to "string", "description" to "Relativer Zielpfad")
                    ),
                    "required" to listOf("sourcePath", "destinationPath")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "move_file",
                "description" to "Verschiebt oder benennt eine Datei oder ein Verzeichnis innerhalb des App-Sandboxes um.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "sourcePath" to mapOf("type" to "string", "description" to "Relativer Quellpfad"),
                        "destinationPath" to mapOf("type" to "string", "description" to "Relativer Zielpfad")
                    ),
                    "required" to listOf("sourcePath", "destinationPath")
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "git_status",
                "description" to "Zeigt den Git-Status des aktuellen Projekts inklusive Branch und geänderten Dateien.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf<String, Any>()
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "git_diff",
                "description" to "Zeigt den Git-Diff des aktuellen Projekts oder eines einzelnen Pfads.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Optionaler relativer Pfad zu einer Datei oder einem Ordner"),
                        "cached" to mapOf("type" to "boolean", "description" to "Wenn true, wird der staged Diff angezeigt", "default" to false)
                    )
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "git_log",
                "description" to "Zeigt die letzten Git-Commits des aktuellen Projekts.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "limit" to mapOf("type" to "number", "description" to "Maximale Anzahl Commits", "default" to 20),
                        "path" to mapOf("type" to "string", "description" to "Optionaler relativer Pfad für gefilterte Historie")
                    )
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "git_show",
                "description" to "Zeigt Details zu einem Git-Commit oder eine Commit-Diff-Zusammenfassung.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "rev" to mapOf("type" to "string", "description" to "Commit-Referenz oder HEAD", "default" to "HEAD"),
                        "path" to mapOf("type" to "string", "description" to "Optionaler relativer Pfad"),
                        "cached" to mapOf("type" to "boolean", "description" to "Nicht zutreffend, reserviert für Konsistenz", "default" to false)
                    )
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "project_inventory",
                "description" to "Erstellt ein kompaktes Inventar des Projekts mit Dateitypen, Schwerpunktbereichen und großen Hotspots.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Optionaler Startpfad im Sandbox-Projekt", "default" to "."),
                        "maxResults" to mapOf("type" to "number", "description" to "Maximale Anzahl der Hotspots", "default" to 10)
                    )
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "ui_action_audit",
                "description" to "Analysiert Compose-UI-Dateien auf doppelte Buttons, Labels und andere visuelle Aktions-Hotspots.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Optionaler UI-Startpfad", "default" to "app/src/main/java/com/example/bamachat/ui"),
                        "maxResults" to mapOf("type" to "number", "description" to "Maximale Anzahl der gemeldeten Treffer", "default" to 20)
                    )
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "config_audit",
                "description" to "Sucht in Settings- und Preset-Dateien nach doppelten Konfigurationswerten, Defaults und mehrfach definierten UI-Listen.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Optionaler Startpfad", "default" to "app/src/main/java/com/example/bamachat"),
                        "maxResults" to mapOf("type" to "number", "description" to "Maximale Anzahl der gemeldeten Treffer", "default" to 20)
                    )
                )
            )
        ),
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "delete_file",
                "description" to "Löscht eine Datei oder einen leeren Ordner im App-Sandbox.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Relativer Pfad zur Datei oder zum Ordner")
                    ),
                    "required" to listOf("path")
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
                    "list_files" -> {
                        val path = args["path"]?.toString() ?: return@withContext error("path fehlt")
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val dir = sandboxFile(basePath, path)
                        if (!dir.exists()) return@withContext error("Ordner nicht gefunden: $path")
                        if (!dir.isDirectory) return@withContext error("Kein Ordner: $path")
                        val recursive = args["recursive"] == true
                        val files = dir.listFiles()?.toList() ?: emptyList()
                        val sb = StringBuilder()
                        fun listRec(f: File, indent: String) {
                            val prefix = if (f.isDirectory) "📁 " else "📄 "
                            sb.appendLine("$indent$prefix${f.name}${if (f.isDirectory) "/" else "  (${f.length()} B)"}")
                            if (recursive && f.isDirectory) {
                                f.listFiles()?.sorted()?.forEach { listRec(it, "$indent  ") }
                            }
                        }
                        files.sortedBy { it.name }.forEach { listRec(it, "") }
                        if (sb.isEmpty()) sb.append("(leer)")
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = sb.toString().trim())))
                    }
                    "search_files" -> {
                        val path = args["path"]?.toString() ?: return@withContext error("path fehlt")
                        val query = args["query"]?.toString()?.trim().orEmpty()
                        if (query.isBlank()) return@withContext error("query fehlt")
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val start = sandboxFile(basePath, path)
                        if (!start.exists()) return@withContext error("Pfad nicht gefunden: $path")
                        val recursive = args["recursive"] != false
                        val searchContents = args["searchContents"] != false
                        val maxResults = (args["maxResults"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50
                        val sandboxRoot = File(basePath).normalize().absoluteFile
                        val queryLower = query.lowercase(Locale.ROOT)

                        val candidates: Sequence<File> = when {
                            start.isFile -> sequenceOf(start)
                            recursive -> start.walkTopDown()
                            else -> start.listFiles()?.asSequence() ?: emptySequence()
                        }.filter { it.isFile }

                        val matches = mutableListOf<String>()
                        var skipped = 0

                        for (file in candidates) {
                            if (matches.size >= maxResults) {
                                skipped++
                                continue
                            }

                            val relativePath = file.relativeTo(sandboxRoot).path
                            val nameMatch = file.name.contains(query, ignoreCase = true) ||
                                relativePath.contains(query, ignoreCase = true)

                            if (nameMatch) {
                                matches += "📄 $relativePath"
                                continue
                            }

                            if (!searchContents || file.length() > 512_000L) continue

                            val text = runCatching { file.readText() }.getOrNull() ?: continue
                            if (!text.contains(query, ignoreCase = true)) continue

                            val lineMatch = text.lineSequence().withIndex().firstOrNull { it.value.contains(query, ignoreCase = true) }
                            val snippet = lineMatch?.value?.trim()?.take(180).orEmpty()
                            val lineInfo = lineMatch?.index?.plus(1)?.let { ":$it" }.orEmpty()
                            matches += buildString {
                                append("📄 ")
                                append(relativePath)
                                append(lineInfo)
                                if (snippet.isNotBlank()) {
                                    append("\n   ")
                                    append(snippet.replace("\t", " ").replace(Regex("\\s+"), " "))
                                }
                            }
                        }

                        val output = buildString {
                            if (matches.isEmpty()) {
                                append("Keine Treffer für '$query' unter '$path'.")
                            } else {
                                appendLine("Treffer für '$query' unter '$path':")
                                matches.forEachIndexed { index, item ->
                                    appendLine("${index + 1}. $item")
                                }
                                if (skipped > 0) {
                                    append("... +$skipped weitere Treffer")
                                }
                            }
                        }
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = output.trim())))
                    }
                    "copy_file" -> {
                        val sourcePath = args["sourcePath"]?.toString() ?: return@withContext error("sourcePath fehlt")
                        val destinationPath = args["destinationPath"]?.toString() ?: return@withContext error("destinationPath fehlt")
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val source = sandboxFile(basePath, sourcePath)
                        val destination = sandboxFile(basePath, destinationPath)
                        if (!source.exists()) return@withContext error("Datei nicht gefunden: $sourcePath")
                        if (destination.exists()) return@withContext error("Ziel existiert bereits: $destinationPath")
                        destination.parentFile?.mkdirs()
                        val copied = if (source.isDirectory) {
                            source.copyRecursively(destination, overwrite = false)
                        } else {
                            source.copyTo(destination, overwrite = false)
                            true
                        }
                        if (!copied) return@withContext error("Konnte nicht kopieren: $sourcePath")
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = "Kopiert: $sourcePath -> $destinationPath")))
                    }
                    "move_file" -> {
                        val sourcePath = args["sourcePath"]?.toString() ?: return@withContext error("sourcePath fehlt")
                        val destinationPath = args["destinationPath"]?.toString() ?: return@withContext error("destinationPath fehlt")
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val source = sandboxFile(basePath, sourcePath)
                        val destination = sandboxFile(basePath, destinationPath)
                        if (!source.exists()) return@withContext error("Datei nicht gefunden: $sourcePath")
                        if (destination.exists()) return@withContext error("Ziel existiert bereits: $destinationPath")
                        destination.parentFile?.mkdirs()
                        val moved = if (source.renameTo(destination)) {
                            true
                        } else if (source.isDirectory) {
                            source.copyRecursively(destination, overwrite = false) && source.deleteRecursively()
                        } else {
                            source.copyTo(destination, overwrite = false)
                            source.delete()
                        }
                        if (!moved) return@withContext error("Konnte nicht verschieben: $sourcePath")
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = "Verschoben: $sourcePath -> $destinationPath")))
                    }
                    "git_status" -> {
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val (exitCode, output) = runGitCommand(
                            basePath,
                            listOf("status", "--short", "--branch", "--untracked-files=all")
                        )
                        val text = formatGitOutput(exitCode, output, 12000)
                        McpToolResult(
                            success = exitCode == 0,
                            content = listOf(McpContentItem(type = "text", text = text))
                        )
                    }
                    "git_diff" -> {
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val path = args["path"]?.toString()?.trim().orEmpty()
                        val cached = args["cached"] == true
                        val command = mutableListOf("diff", "--unified=3").apply {
                            if (cached) add("--cached")
                            if (path.isNotBlank()) {
                                add("--")
                                add(path)
                            }
                        }
                        val (exitCode, output) = runGitCommand(basePath, command)
                        val text = formatGitOutput(exitCode, output, 20000)
                        McpToolResult(
                            success = exitCode == 0,
                            content = listOf(McpContentItem(type = "text", text = text))
                        )
                    }
                    "git_log" -> {
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 20
                        val path = args["path"]?.toString()?.trim().orEmpty()
                        val command = mutableListOf(
                            "log",
                            "--oneline",
                            "--decorate",
                            "--graph",
                            "-n",
                            limit.toString()
                        ).apply {
                            if (path.isNotBlank()) {
                                add("--")
                                add(path)
                            }
                        }
                        val (exitCode, output) = runGitCommand(basePath, command)
                        val text = formatGitOutput(exitCode, output, 18000)
                        McpToolResult(
                            success = exitCode == 0,
                            content = listOf(McpContentItem(type = "text", text = text))
                        )
                    }
                    "git_show" -> {
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val rev = args["rev"]?.toString()?.trim().orEmpty().ifBlank { "HEAD" }
                        val path = args["path"]?.toString()?.trim().orEmpty()
                        val command = mutableListOf("show", "--stat", "--patch", "--format=medium", rev).apply {
                            if (path.isNotBlank()) {
                                add("--")
                                add(path)
                            }
                        }
                        val (exitCode, output) = runGitCommand(basePath, command)
                        val text = formatGitOutput(exitCode, output, 22000)
                        McpToolResult(
                            success = exitCode == 0,
                            content = listOf(McpContentItem(type = "text", text = text))
                        )
                    }
                    "project_inventory" -> {
                        val path = args["path"]?.toString()?.trim().orEmpty().ifBlank { "." }
                        val maxResults = (args["maxResults"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 10
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val root = sandboxFile(basePath, path)
                        if (!root.exists()) return@withContext error("Pfad nicht gefunden: $path")
                        val files = if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile }.toList()
                        val allFilesCount = files.size
                        val extensionCounts = files.groupingBy { it.extension.ifBlank { "[ohne]" } }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }
                        val kotlinFiles = files.filter { it.extension == "kt" }
                        val screenCount = kotlinFiles.count { it.name.endsWith("Screen.kt") }
                        val viewModelCount = kotlinFiles.count { it.name.endsWith("ViewModel.kt") }
                        val componentCount = kotlinFiles.count { it.name.endsWith("Component.kt") || it.name.endsWith("Components.kt") }
                        val utilCount = kotlinFiles.count { it.path.contains("${File.separator}util${File.separator}") }
                        val serviceCount = kotlinFiles.count { it.path.contains("${File.separator}service${File.separator}") }
                        val topHotspots = files
                            .sortedByDescending { it.length() }
                            .take(maxResults)
                        val output = buildString {
                            appendLine("Projektinventar für '$path'")
                            appendLine("- Dateien: $allFilesCount")
                            appendLine("- Kotlin-Dateien: ${kotlinFiles.size}")
                            appendLine("- Screens: $screenCount")
                            appendLine("- ViewModels: $viewModelCount")
                            appendLine("- Components: $componentCount")
                            appendLine("- Utilities: $utilCount")
                            appendLine("- Services: $serviceCount")
                            if (extensionCounts.isNotEmpty()) {
                                appendLine("- Dateitypen:")
                                extensionCounts.take(8).forEach { (ext, count) ->
                                    appendLine("  - .$ext: $count")
                                }
                            }
                            if (topHotspots.isNotEmpty()) {
                                appendLine("- Größte Hotspots:")
                                topHotspots.forEachIndexed { index, file ->
                                    appendLine("  ${index + 1}. ${file.relativeTo(File(basePath).normalize().absoluteFile).path} (${file.length()} B)")
                                }
                            }
                        }.trim()
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = output)))
                    }
                    "ui_action_audit" -> {
                        val path = args["path"]?.toString()?.trim().orEmpty().ifBlank { "app/src/main/java/com/example/bamachat/ui" }
                        val maxResults = (args["maxResults"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 20
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val root = sandboxFile(basePath, path)
                        if (!root.exists()) return@withContext error("Pfad nicht gefunden: $path")
                        val files = if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                        val base = File(basePath).normalize().absoluteFile
                        val labelPatterns = listOf(
                            Regex("Text\\(\"([^\"\\n]{2,120})\""),
                            Regex("contentDescription\\s*=\\s*\"([^\"\\n]{2,120})\""),
                            Regex("label\\s*=\\s*\\{\\s*Text\\(\"([^\"\\n]{2,120})\""),
                            Regex("title\\s*=\\s*\\{\\s*Text\\(\"([^\"\\n]{2,120})\"")
                        )
                        val widgetPattern = Regex("\\b(Button|TextButton|OutlinedButton|IconButton|AssistChip|FilterChip|FilledIconButton|FilledTonalButton|DropdownMenuItem)\\b")
                        val labelHits = linkedMapOf<String, MutableSet<String>>()
                        val fileWidgetCounts = mutableListOf<Pair<String, Int>>()

                        files.forEach { file ->
                            val relative = file.relativeTo(base).path
                            val text = runCatching { file.readText() }.getOrNull().orEmpty()
                            if (text.isBlank()) return@forEach

                            labelPatterns.forEach { pattern ->
                                pattern.findAll(text).forEach { match ->
                                    val label = match.groupValues.getOrNull(1)?.trim().orEmpty()
                                    if (label.isNotBlank()) {
                                        labelHits.getOrPut(label) { linkedSetOf() }.add(relative)
                                    }
                                }
                            }

                            val widgetCount = widgetPattern.findAll(text).count()
                            fileWidgetCounts += relative to widgetCount
                        }

                        val duplicateLabels = labelHits.entries
                            .filter { it.value.size > 1 }
                            .sortedWith(compareByDescending<Map.Entry<String, MutableSet<String>>> { it.value.size }.thenBy { it.key.lowercase(Locale.ROOT) })
                            .take(maxResults)
                        val hotspotFiles = fileWidgetCounts
                            .filter { it.second > 0 }
                            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase(Locale.ROOT) })
                            .take(maxResults)
                        val output = buildString {
                            appendLine("UI-Audit für '$path'")
                            appendLine("- Dateien gescannt: ${files.size}")
                            appendLine("- Doppelte Labels:")
                            if (duplicateLabels.isEmpty()) {
                                appendLine("  - Keine offensichtlichen Label-Duplikate gefunden.")
                            } else {
                                duplicateLabels.forEach { (label, filesWithLabel) ->
                                    appendLine("  - \"$label\" in ${filesWithLabel.size} Dateien")
                                    filesWithLabel.take(5).forEach { file ->
                                        appendLine("    - $file")
                                    }
                                }
                            }
                            appendLine("- Aktions-Hotspots:")
                            if (hotspotFiles.isEmpty()) {
                                appendLine("  - Keine Hotspots gefunden.")
                            } else {
                                hotspotFiles.forEach { (file, count) ->
                                    appendLine("  - $file: $count sichtbare Aktions-Elemente")
                                }
                            }
                        }.trim()
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = output)))
                    }
                    "config_audit" -> {
                        val path = args["path"]?.toString()?.trim().orEmpty().ifBlank { "app/src/main/java/com/example/bamachat" }
                        val maxResults = (args["maxResults"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 20
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val root = sandboxFile(basePath, path)
                        if (!root.exists()) return@withContext error("Pfad nicht gefunden: $path")
                        val files = if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                        val trackedLabels = listOf(
                            "Generalist",
                            "Recherche",
                            "Entwickler",
                            "Marketing",
                            "Lager & Logistik",
                            "Optimierer",
                            "Klar und präzise",
                            "Analytisch",
                            "Schritt-für-Schritt",
                            "Kreativ",
                            "Kurz mit Bulletpoints"
                        )
                        val hits = linkedMapOf<String, MutableSet<String>>()
                        val base = File(basePath).normalize().absoluteFile
                        files.forEach { file ->
                            val relative = file.relativeTo(base).path
                            val text = runCatching { file.readText() }.getOrNull().orEmpty()
                            if (text.isBlank()) return@forEach
                            trackedLabels.forEach { label ->
                                if (text.contains(label)) {
                                    hits.getOrPut(label) { linkedSetOf() }.add(relative)
                                }
                            }
                        }
                        val duplicateConfigs = hits.entries
                            .filter { it.value.size > 1 }
                            .sortedWith(compareByDescending<Map.Entry<String, MutableSet<String>>> { it.value.size }.thenBy { it.key.lowercase(Locale.ROOT) })
                            .take(maxResults)
                        val output = buildString {
                            appendLine("Konfigurations-Audit für '$path'")
                            appendLine("- Dateien gescannt: ${files.size}")
                            appendLine("- Mehrfach verwendete Preset-/Style-Werte:")
                            if (duplicateConfigs.isEmpty()) {
                                appendLine("  - Keine mehrfach genutzten Werte gefunden.")
                            } else {
                                duplicateConfigs.forEach { (label, filesWithLabel) ->
                                    appendLine("  - \"$label\" in ${filesWithLabel.size} Dateien")
                                    filesWithLabel.take(5).forEach { file ->
                                        appendLine("    - $file")
                                    }
                                }
                            }
                            appendLine("- Hinweis:")
                            appendLine("  - Wenn dieselben Listen in mehreren Screens auftauchen, lohnt sich ein gemeinsames Catalog-/Library-Modell.")
                        }.trim()
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = output)))
                    }
                    "delete_file" -> {
                        val path = args["path"]?.toString() ?: return@withContext error("path fehlt")
                        if (basePath.isBlank()) return@withContext error("Dateizugriff nicht verfügbar")
                        val file = sandboxFile(basePath, path)
                        if (!file.exists()) return@withContext error("Datei nicht gefunden: $path")
                        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                        if (!deleted) return@withContext error("Konnte nicht löschen: $path")
                        McpToolResult(success = true, content = listOf(McpContentItem(type = "text", text = "Gelöscht: $path")))
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

    private fun runGitCommand(basePath: String, args: List<String>, timeoutSeconds: Long = 30L): Pair<Int, String> {
        val command = listOf("git", "-C", basePath) + args
        val proc = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            proc.waitFor()
        }
        val output = proc.inputStream.bufferedReader().readText()
        return proc.exitValue() to output
    }

    private fun formatGitOutput(exitCode: Int, output: String, limit: Int): String {
        val trimmed = output.trim().ifBlank { "(keine Ausgabe)" }
        val text = if (trimmed.length > limit) trimmed.take(limit) + "\n\n[truncated]" else trimmed
        return "Exit $exitCode:\n$text"
    }

    private fun error(msg: String) = McpToolResult(success = false, content = listOf(McpContentItem(type = "text", text = msg)), isError = true)
}
