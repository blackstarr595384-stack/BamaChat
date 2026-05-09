package com.example.bamachat.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object AppTelemetry {
    @Volatile
    private var initialized = false
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context, enableCrashlytics: Boolean) {
        if (initialized) return
        analytics = FirebaseAnalytics.getInstance(context.applicationContext)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enableCrashlytics)
        initialized = true
    }

    fun setUserId(userId: String?) {
        val safeId = userId?.takeIf { it.isNotBlank() } ?: "guest"
        runCatching {
            analytics?.setUserId(safeId)
            FirebaseCrashlytics.getInstance().setUserId(safeId)
        }
    }

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            bundle.putString(key.take(40), value.take(100))
        }
        runCatching { analytics?.logEvent(name.take(40), bundle) }
    }

    fun logError(tag: String, throwable: Throwable, message: String? = null) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("[${tag.take(40)}] ${message?.take(180) ?: throwable.message.orEmpty()}")
            crashlytics.recordException(throwable)
        }
    }
}
