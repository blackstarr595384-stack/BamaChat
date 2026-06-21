package com.example.bamachat.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object AppTelemetry {

    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    @Volatile
    private var collectionEnabled: Boolean = false

    fun initialize(context: Context, collectionEnabled: Boolean = false) {
        analytics = FirebaseAnalytics.getInstance(context)
        crashlytics = FirebaseCrashlytics.getInstance()
        setCollectionEnabled(collectionEnabled)
    }

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        analytics?.setAnalyticsCollectionEnabled(enabled)
        crashlytics?.setCrashlyticsCollectionEnabled(enabled)
    }

    fun isCollectionEnabled(): Boolean = collectionEnabled

    private fun telemetryReady(): Boolean {
        return collectionEnabled && analytics != null && crashlytics != null
    }

    fun logEvent(eventName: String) {
        logEvent(eventName, emptyMap())
    }

    fun logEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        if (!telemetryReady()) return
        try {
            val bundle = Bundle()
            params.forEach { (key, value) ->
                bundle.putString(key, value)
            }
            analytics?.logEvent(eventName, bundle)
        } catch (e: Exception) {
            android.util.Log.e("AppTelemetry", "Failed to log event: $eventName", e)
        }
    }

    fun logError(tag: String, exception: Throwable? = null) {
        if (!telemetryReady()) return
        try {
            if (exception != null) {
                crashlytics?.recordException(exception)
            }
            crashlytics?.log("ERROR: $tag - ${exception?.message}")
        } catch (e: Exception) {
            android.util.Log.e("AppTelemetry", "Failed to log error", e)
        }
    }

    fun setUserProperty(key: String, value: String) {
        if (!telemetryReady()) return
        try {
            analytics?.setUserProperty(key, value)
        } catch (e: Exception) {
            android.util.Log.e("AppTelemetry", "Failed to set user property", e)
        }
    }

    fun setUserId(userId: String?) {
        if (!telemetryReady()) return
        try {
            analytics?.setUserId(userId)
        } catch (e: Exception) {
            android.util.Log.e("AppTelemetry", "Failed to set user ID", e)
        }
    }

    fun logTiming(name: String, durationMs: Long, params: Map<String, String> = emptyMap()) {
        if (!telemetryReady()) return
        val timingParams = params.toMutableMap()
        timingParams["duration_ms"] = durationMs.coerceAtLeast(0L).toString()
        logEvent(name, timingParams)
    }

    fun trackScreenView(screenName: String) {
        if (!telemetryReady()) return
        logEvent("screen_view", mapOf("screen_name" to screenName))
    }

    fun trackChatMessage(provider: String, modelName: String) {
        if (!telemetryReady()) return
        logEvent("chat_message_sent", mapOf(
            "provider" to provider,
            "model" to modelName,
            "timestamp" to System.currentTimeMillis().toString()
        ))
    }

    fun trackPurchase(tier: String, priceUsd: String) {
        if (!telemetryReady()) return
        logEvent("purchase_completed", mapOf(
            "tier" to tier,
            "price" to priceUsd
        ))
    }
}
