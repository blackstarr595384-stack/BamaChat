package com.example.bamachat.voice

import com.example.bamachat.util.AppTelemetry

object AppVoiceDiagnostics : VoiceDiagnostics {
    override fun event(name: String, attributes: Map<String, String>) {
        AppTelemetry.logEvent(name, attributes)
    }

    override fun timing(name: String, durationMs: Long, attributes: Map<String, String>) {
        AppTelemetry.logTiming(name, durationMs, attributes)
    }
}
