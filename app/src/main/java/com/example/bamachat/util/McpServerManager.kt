package com.example.bamachat.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class McpServerManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mcp_servers", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val clients = mutableMapOf<String, McpClient>()
    private val manualConnectionStates = mutableMapOf<String, McpConnectionStatus>()
    private val serverMutationLock = Any()

    private val _servers = MutableStateFlow(loadServers())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    private val _allTools = MutableStateFlow<List<McpToolDefinition>>(emptyList())
    val allTools: StateFlow<List<McpToolDefinition>> = _allTools.asStateFlow()

    private val _connectionStates = MutableStateFlow<Map<String, McpConnectionStatus>>(emptyMap())
    val connectionStates: StateFlow<Map<String, McpConnectionStatus>> = _connectionStates.asStateFlow()

    init {
        ensureDefaultServersRegistered()
        refreshConnectionStates()
    }

    fun getClient(serverId: String): McpClient? = clients[serverId]

    suspend fun startServer(serverId: String) {
        val existingClient = clients[serverId]
        if (existingClient != null) {
            val status = existingClient.connectionStatus.value
            if (status == McpConnectionStatus.CONNECTED || status == McpConnectionStatus.CONNECTING) return
            stopServer(serverId)
        }

        val baseConfig = _servers.value.find { it.id == serverId } ?: return
        val config = resolveStartConfig(baseConfig)
        if (config == null) {
            manualConnectionStates[serverId] = McpConnectionStatus.ERROR
            refreshConnectionStates()
            return
        }

        manualConnectionStates.remove(serverId)
        val client = McpClient(serverId, config)
        clients[serverId] = client
        client.start()
        if (client.connectionStatus.value == McpConnectionStatus.ERROR) {
            manualConnectionStates[serverId] = McpConnectionStatus.ERROR
        }
        refreshTools()
        _servers.update { it.toList() }
    }

    suspend fun stopServer(serverId: String) {
        clients[serverId]?.stop()
        clients.remove(serverId)
        manualConnectionStates[serverId] = McpConnectionStatus.DISCONNECTED
        refreshTools()
        _servers.update { it.toList() }
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
        refreshConnectionStates()
    }

    private fun refreshConnectionStates() {
        val states = _servers.value.associate { server ->
            server.id to (clients[server.id]?.connectionStatus?.value ?: McpConnectionStatus.DISCONNECTED)
        }.toMutableMap()
        manualConnectionStates.forEach { (id, state) -> states[id] = state }
        _connectionStates.value = states
    }

    suspend fun callTool(name: String, arguments: Map<String, Any> = emptyMap()): McpToolResult {
        val builtinNames = BuiltinTools.definitions.map { (it["function"] as? Map<*, *>)?.get("name")?.toString() }.filterNotNull()
        if (name in builtinNames) {
            return BuiltinTools.execute(name, arguments, context.filesDir.absolutePath)
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

    private fun ensureDefaultServersRegistered() {
        val loaded = _servers.value
        if (loaded.isEmpty()) {
            saveServers(defaultMcpServers)
            _servers.value = defaultMcpServers
            return
        }

        val merged = loaded.toMutableList()
        val existingIds = loaded.map { it.id }.toHashSet()
        var changed = false
        for (defaultConfig in defaultMcpServers) {
            if (!existingIds.contains(defaultConfig.id)) {
                merged.add(defaultConfig)
                changed = true
            }
        }
        if (changed) {
            saveServers(merged)
            _servers.value = merged.toList()
        }
    }

    private fun resolveStartConfig(config: McpServerConfig): McpServerConfig? {
        if (config.id != "remote-bridge" && config.command != "remote_http") return config
        val endpoint = settingsPrefs.getString("mcp_remote_url", "")?.trim().orEmpty()
        if (endpoint.isBlank()) {
            return null
        }
        val token = SecureSettingsStore.getString(context, settingsPrefs, "mcp_remote_token", "").trim()
        val env = if (token.isBlank()) config.env else config.env + ("MCP_REMOTE_TOKEN" to token)
        return config.copy(command = endpoint, args = emptyList(), env = env)
    }

    private fun saveServers(servers: List<McpServerConfig>) {
        prefs.edit().putString("server_list", gson.toJson(servers)).apply()
    }

    fun addServer(config: McpServerConfig) {
        synchronized(serverMutationLock) {
            val updated = _servers.value.filterNot { it.id == config.id } + config
            saveServers(updated)
            _servers.value = updated
        }
    }

    fun removeServer(serverId: String) {
        kotlinx.coroutines.runBlocking { stopServer(serverId) }
        synchronized(serverMutationLock) {
            val updated = _servers.value.filterNot { it.id == serverId }
            saveServers(updated)
            _servers.value = updated
        }
    }

    fun toggleServer(serverId: String) {
        synchronized(serverMutationLock) {
            val old = _servers.value
            val updated = old.map { if (it.id == serverId) it.copy(enabled = !it.enabled) else it }
            if (updated != old) {
                saveServers(updated)
                _servers.value = updated
            }
        }
    }
}
