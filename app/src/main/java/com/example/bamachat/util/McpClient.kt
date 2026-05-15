package com.example.bamachat.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McpClient(
    val serverId: String,
    private val config: McpServerConfig
) {
    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private var readerJob: kotlinx.coroutines.Job? = null
    private val pendingRequests = ConcurrentHashMap<String, Channel<JSONObject>>()
    private val _tools = MutableStateFlow<List<McpToolDefinition>>(emptyList())
    val tools: StateFlow<List<McpToolDefinition>> = _tools.asStateFlow()
    private val _connectionStatus = MutableStateFlow(McpConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<McpConnectionStatus> = _connectionStatus.asStateFlow()
    private var initialized = false

    suspend fun start() = withContext(Dispatchers.IO) {
        if (process != null) return@withContext
        try {
            _connectionStatus.value = McpConnectionStatus.CONNECTING
            val pb = ProcessBuilder(config.command, *config.args.toTypedArray())
                .redirectErrorStream(false)
            config.env.forEach { (k, v) -> pb.environment()[k] = v }
            process = pb.start()
            writer = OutputStreamWriter(process!!.outputStream)
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            startReader()
            sendInitialize()
            _connectionStatus.value = McpConnectionStatus.CONNECTED
            Log.i("McpClient", "MCP Server '$serverId' gestartet")
        } catch (e: Exception) {
            Log.e("McpClient", "Fehler beim Start von '$serverId'", e)
            _connectionStatus.value = McpConnectionStatus.ERROR
        }
    }

    suspend fun listTools(): List<McpToolDefinition> {
        if (!initialized) return emptyList()
        return try {
            val response = sendRequest("tools/list")
            val toolsArr = response.optJSONArray("tools") ?: JSONArray()
            val result = mutableListOf<McpToolDefinition>()
            for (i in 0 until toolsArr.length()) {
                val t = toolsArr.getJSONObject(i)
                result += McpToolDefinition(
                    name = t.getString("name"),
                    description = t.optString("description", ""),
                    inputSchema = t.optJSONObject("inputSchema")?.toMap().orEmpty(),
                    serverId = serverId
                )
            }
            _tools.value = result
            result
        } catch (e: Exception) {
            Log.e("McpClient", "listTools failed", e)
            emptyList()
        }
    }

    suspend fun callTool(name: String, arguments: Map<String, Any> = emptyMap()): McpToolResult {
        return try {
            val response = sendRequest("tools/call", mapOf(
                "name" to name,
                "arguments" to arguments
            ))
            val contentArr = response.optJSONArray("content") ?: JSONArray()
            val items = mutableListOf<McpContentItem>()
            for (i in 0 until contentArr.length()) {
                val c = contentArr.getJSONObject(i)
                items += McpContentItem(
                    type = c.optString("type", "text"),
                    text = c.optString("text", null),
                    data = c.optString("data", null),
                    mimeType = c.optString("mimeType", null)
                )
            }
            McpToolResult(
                success = true,
                content = items,
                isError = response.optBoolean("isError", false)
            )
        } catch (e: Exception) {
            McpToolResult(
                success = false,
                content = listOf(McpContentItem(type = "text", text = "Fehler: ${e.message}")),
                isError = true
            )
        }
    }

    fun stop() {
        try {
            readerJob?.cancel()
            readerJob = null
            process?.destroy()
            process = null
            writer = null
            reader = null
            initialized = false
            _tools.value = emptyList()
            _connectionStatus.value = McpConnectionStatus.DISCONNECTED
            pendingRequests.forEach { (_, ch) -> ch.close() }
            pendingRequests.clear()
        } catch (_: Exception) {}
    }

    private fun startReader() {
        readerJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    if (line.isBlank()) continue
                    try {
                        val json = JSONObject(line)
                        val id = json.optString("id", "")
                        if (id.isNotEmpty() && pendingRequests.containsKey(id)) {
                            pendingRequests[id]?.trySend(json)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun sendInitialize() {
        val response = sendRequest("initialize", mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to emptyMap<String, Any>(),
            "clientInfo" to mapOf("name" to "bamachat", "version" to "1.0")
        ))
        initialized = response.has("capabilities")
        sendRequest("notifications/initialized", null)
        listTools()
    }

    private suspend fun sendRequest(method: String, params: Any? = null): JSONObject {
        val id = UUID.randomUUID().toString()
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        val channel = Channel<JSONObject>(1)
        pendingRequests[id] = channel
        writer?.write(request.toString() + "\n")
        writer?.flush()
        val response = channel.receive()
        val error = response.optJSONObject("error")
        if (error != null) {
            throw RuntimeException("MCP Error: ${error.optString("message", "unknown")}")
        }
        return response.optJSONObject("result") ?: JSONObject()
    }
}

enum class McpConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        val value = get(key)
        map[key] = when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> {
                val list = mutableListOf<Any>()
                for (i in 0 until value.length()) {
                    val v = value[i]
                    list.add(if (v is JSONObject) v.toMap() else v)
                }
                list
            }
            else -> value
        }
    }
    return map
}
