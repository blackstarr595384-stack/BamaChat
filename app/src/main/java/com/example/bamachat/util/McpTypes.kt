package com.example.bamachat.util

data class McpServerConfig(
    val id: String,
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val autoStart: Boolean = false
)

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any> = emptyMap(),
    val serverId: String = ""
)

data class McpToolCall(
    val name: String,
    val arguments: Map<String, Any> = emptyMap()
)

data class McpToolResult(
    val success: Boolean,
    val content: List<McpContentItem>,
    val isError: Boolean = false
)

data class McpContentItem(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

val defaultMcpServers = listOf(
    McpServerConfig(
        id = "filesystem",
        name = "Dateisystem",
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-filesystem"),
        autoStart = false
    ),
    McpServerConfig(
        id = "web-search",
        name = "Web-Suche",
        command = "npx",
        args = listOf("-y", "@anthropic-ai/mcp-server-web-search"),
        autoStart = false
    ),
    McpServerConfig(
        id = "memory",
        name = "Knowledge Graph",
        command = "npx",
        args = listOf("-y", "@modelcontextprotocol/server-memory"),
        autoStart = false
    ),
    McpServerConfig(
        id = "code-executor",
        name = "Code-Ausführung",
        command = "npx",
        args = listOf("-y", "@anthropic-ai/mcp-server-code-executor"),
        autoStart = false
    )
)
