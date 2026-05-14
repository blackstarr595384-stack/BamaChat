package com.example.bamachat.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object AppTelemetry {
    private const val TAG = "AppTelemetry"
    @Volatile
    private var initialized = false
    @Volatile
    private var firebaseAvailable = false
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context, enableCrashlytics: Boolean) {
        if (initialized) return
        val appContext = context.applicationContext
        val app = runCatching { FirebaseApp.getApps(appContext).firstOrNull() }
            .getOrNull()
            ?: runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
        firebaseAvailable = app != null
        if (firebaseAvailable) {
            analytics = runCatching { FirebaseAnalytics.getInstance(appContext) }.getOrNull()
            runCatching {
                FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enableCrashlytics)
            }
        }
        initialized = true
    }

    fun setUserId(userId: String?) {
        if (!firebaseAvailable) return
        val safeId = userId?.takeIf { it.isNotBlank() } ?: "guest"
        runCatching {
            analytics?.setUserId(safeId)
            FirebaseCrashlytics.getInstance().setUserId(safeId)
        }
    }

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        if (!firebaseAvailable) {
            Log.d(TAG, "event=$name params=$params")
            return
        }
        val bundle = Bundle()
        params.forEach { (key, value) ->
            bundle.putString(key.take(40), value.take(100))
        }
        runCatching { analytics?.logEvent(name.take(40), bundle) }
    }

    fun logTiming(name: String, durationMs: Long, params: Map<String, String> = emptyMap()) {
        logEvent(
            name = name,
            params = params + ("duration_ms" to durationMs.coerceAtLeast(0L).toString())
        )
    }

    fun logError(tag: String, throwable: Throwable, message: String? = null) {
        if (!firebaseAvailable) {
            Log.e(TAG, "[$tag] ${message ?: throwable.message}", throwable)
            return
        }
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("[${tag.take(40)}] ${message?.take(180) ?: throwable.message.orEmpty()}")
            crashlytics.recordException(throwable)
        }
    }
}
