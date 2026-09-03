package com.example.bamachat.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bamachat.data.AndroidAiOrchestrator
import com.example.bamachat.data.SharedAiPilotDebugToggle
import com.example.bamachat.util.AppTelemetry

class SharedAiPilotDebugToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SharedAiPilotDebugToggle.ACTION_SET_ENABLED) return

        val enabled = SharedAiPilotDebugToggle.resolveRequestedEnabled(
            hasEnabledExtra = intent.hasExtra(SharedAiPilotDebugToggle.EXTRA_ENABLED),
            enabled = intent.getBooleanExtra(SharedAiPilotDebugToggle.EXTRA_ENABLED, false)
        )
        val streamingEnabled = SharedAiPilotDebugToggle.resolveRequestedStreamingEnabled(
            hasStreamingEnabledExtra = intent.hasExtra(SharedAiPilotDebugToggle.EXTRA_STREAMING_ENABLED),
            streamingEnabled = intent.getBooleanExtra(SharedAiPilotDebugToggle.EXTRA_STREAMING_ENABLED, false),
            fallbackEnabled = enabled
        )
        if (enabled == null && streamingEnabled == null) return

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (enabled != null) {
            editor.putBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_EXPERIMENTAL, enabled)
        }
        if (streamingEnabled != null) {
            editor.putBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_STREAMING_PILOT, streamingEnabled)
        }
        val committed = editor.commit()
        val stored = prefs.getBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_EXPERIMENTAL, false)
        val streamingStored = prefs.getBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_STREAMING_PILOT, false)

        AppTelemetry.logEvent(
            "debug_shared_ai_pilot_toggled",
            mapOf(
                "enabled" to enabled?.toString(),
                "stored" to stored.toString(),
                "streaming_enabled" to streamingEnabled?.toString(),
                "streaming_stored" to streamingStored.toString(),
                "committed" to committed.toString()
            )
        )
    }
}
