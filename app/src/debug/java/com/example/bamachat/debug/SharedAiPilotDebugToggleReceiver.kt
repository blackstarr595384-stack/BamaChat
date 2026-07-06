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
        ) ?: return

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val committed = prefs.edit()
            .putBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_EXPERIMENTAL, enabled)
            .commit()
        val stored = prefs.getBoolean(AndroidAiOrchestrator.KEY_SHARED_AI_EXPERIMENTAL, false)

        AppTelemetry.logEvent(
            "debug_shared_ai_pilot_toggled",
            mapOf(
                "enabled" to enabled.toString(),
                "stored" to stored.toString(),
                "committed" to committed.toString()
            )
        )
    }
}
