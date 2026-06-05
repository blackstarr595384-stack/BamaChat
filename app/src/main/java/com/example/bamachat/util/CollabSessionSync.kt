package com.example.bamachat.util

import android.content.Context
import android.os.Build
import java.util.UUID

class CollabSessionSync(private val context: Context) {

    data class DeviceInfo(
        val deviceId: String,
        val deviceName: String,
        val platform: DevicePlatform,
        val lastActiveAt: Long,
        val isCurrentDevice: Boolean
    )

    enum class DevicePlatform { ANDROID, WINDOWS }

    data class SessionHandoff(
        val sessionId: String,
        val joinCode: String,
        val lastMessageId: String?,
        val lastReadAt: Long,
        val pendingChanges: Int
    )

    private val prefs = context.getSharedPreferences("collab_sync", Context.MODE_PRIVATE)

    fun getDeviceId(): String {
        val key = "device_id"
        var id = prefs.getString(key, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(key, id).apply()
        }
        return id
    }

    fun getDeviceName(): String = Build.MODEL

    fun saveSessionHandoff(sessionId: String, joinCode: String, lastMessageId: String?) {
        val sessions = getActiveSessions().toMutableList()
        val existing = sessions.indexOfFirst { it.sessionId == sessionId }
        val entry = SessionHandoff(
            sessionId = sessionId,
            joinCode = joinCode,
            lastMessageId = lastMessageId,
            lastReadAt = System.currentTimeMillis(),
            pendingChanges = 0
        )
        if (existing >= 0) {
            sessions[existing] = entry
        } else {
            sessions.add(entry)
        }
        saveSessions(sessions)
    }

    fun getActiveSessions(): List<SessionHandoff> {
        val json = prefs.getString("active_sessions", null) ?: return emptyList()
        return try {
            com.google.gson.Gson().fromJson(json, Array<SessionHandoff>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun removeSession(sessionId: String) {
        val sessions = getActiveSessions().filter { it.sessionId != sessionId }
        saveSessions(sessions)
    }

    fun migrateSessionToDevice(sessionId: String, targetDeviceId: String) {
        val sessions = getActiveSessions().toMutableList()
        val idx = sessions.indexOfFirst { it.sessionId == sessionId }
        if (idx < 0) return
        val session = sessions[idx]
        sessions[idx] = session
        saveSessions(sessions)

        prefs.edit().putString("migrate_${sessionId}_to", targetDeviceId).apply()
    }

    private fun saveSessions(sessions: List<SessionHandoff>) {
        val json = com.google.gson.Gson().toJson(sessions)
        prefs.edit().putString("active_sessions", json).apply()
    }

    private fun getMigrationTarget(sessionId: String): String? {
        return prefs.getString("migrate_${sessionId}_to", null)
    }
}
