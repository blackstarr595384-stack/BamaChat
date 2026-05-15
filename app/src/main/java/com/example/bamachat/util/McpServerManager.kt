package com.example.bamachat.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class McpServerManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mcp_servers", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val clients = mutableMapOf<String, McpClient>()

    private val _servers = MutableStateFlow(loadServers())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    private val _allTools = MutableStateFlow<List<McpToolDefinition>>(emptyList())
    val allTools: StateFlow<List<McpToolDefinition>> = _allTools.asStateFlow()

    private val _connectionStates = MutableStateFlow<Map<String, McpConnectionStatus>>(emptyMap())
    val connectionStates: StateFlow<Map<String, McpConnectionStatus>> = _connectionStates.asStateFlow()

    init {
        if (_servers.value.isEmpty()) {
            saveServers(defaultMcpServers)
        }
    }

    fun getClient(serverId: String): McpClient? = clients[serverId]

    suspend fun startServer(serverId: String) {
        val config = _servers.value.find { it.id == serverId } ?: return
        if (clients.containsKey(serverId)) return
        val client = McpClient(serverId, config)
        clients[serverId] = client
        client.start()
        refreshTools()
        _servers.value = _servers.value.toList()
    }

    suspend fun stopServer(serverId: String) {
        clients[serverId]?.stop()
        clients.remove(serverId)
        refreshTools()
        _servers.value = _servers.value.toList()
    }

    fun stopAll() {
        clients.values.forEach { it.stop() }
        clients.clear()
        kotlinx.coroutines.runBlocking {
            refreshTools()
        }
    }

    suspend fun refreshTools() {
        val all = clients.values.flatMap { it.listTools() }
        _allTools.value = all
        _connectionStates.value = clients.mapValues { it.value.connectionStatus.value }
    }

    suspend fun callTool(name: String, arguments: Map<String, Any> = emptyMap()): McpToolResult {
        val builtinNames = BuiltinTools.definitions.map { (it["function"] as? Map<*, *>)?.get("name")?.toString() }.filterNotNull()
        if (name in builtinNames) {
            return BuiltinTools.execute(name, arguments)
        }
        for (client in clients.values) {
            val tool = client.tools.value.find { it.name == name } ?: continue
            return client.callTool(name, arguments)
        }
        return McpToolResult(
            success = false,
            content = listOf(McpContentItem(type = "text", text = "Tool '$name' nicht gefunden")),
            isError = true
        )
    }

    fun getToolDefinitionsOpenAI(): List<Map<String, Any>> {
        return BuiltinTools.definitions + _allTools.value.map { tool ->
            val properties = mutableMapOf<String, Any>()
            val required = mutableListOf<String>()

            val schema = tool.inputSchema
            val props = (schema["properties"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { entry ->
                val prop = entry.value as? Map<*, *> ?: emptyMap<Any, Any>()
                val type = prop["type"]?.toString() ?: "string"
                val desc = prop["description"]?.toString() ?: ""
                properties[entry.key] = mapOf("type" to type, "description" to desc)
                if (prop["required"] == true || (schema["required"] as? List<*>)?.contains(entry.key) == true) {
                    required.add(entry.key)
                }
                mapOf("type" to type, "description" to desc)
            }

            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to properties,
                        "required" to required
                    ).filterKeys { (properties[it] as? Map<*, *>)?.isNotEmpty() != false }
                )
            )
        }
    }

    private fun loadServers(): List<McpServerConfig> {
        val json = prefs.getString("server_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<McpServerConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun saveServers(servers: List<McpServerConfig>) {
        prefs.edit().putString("server_list", gson.toJson(servers)).apply()
    }

    fun addServer(config: McpServerConfig) {
        val current = _servers.value.toMutableList()
        current.removeAll { it.id == config.id }
        current.add(config)
        saveServers(current)
        _servers.value = current
    }

    fun removeServer(serverId: String) {
        kotlinx.coroutines.runBlocking { stopServer(serverId) }
        val current = _servers.value.toMutableList()
        current.removeAll { it.id == serverId }
        saveServers(current)
        _servers.value = current
    }

    fun toggleServer(serverId: String) {
        val current = _servers.value.toMutableList()
        val idx = current.indexOfFirst { it.id == serverId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(enabled = !current[idx].enabled)
            saveServers(current)
            _servers.value = current
        }
    }
}
