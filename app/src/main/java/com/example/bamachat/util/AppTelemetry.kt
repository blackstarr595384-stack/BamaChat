package com.example.bamachat.util

import android.content.Context
import android.os.Bundle
import android.util.Log

object AppTelemetry {
    private const val TAG = "BamaChatTelemetry"

    fun initialize(collectionEnabled: Boolean = false) {
        Log.d(TAG, "Telemetry initialized without Crashlytics. collectionEnabled=$collectionEnabled")
    }

    fun initialize(context: Context, collectionEnabled: Boolean = false) {
        Log.d(TAG, "Telemetry initialized without Crashlytics. context=${context.packageName}, collectionEnabled=$collectionEnabled")
    }

    fun setCollectionEnabled(enabled: Boolean) {
        Log.d(TAG, "Crashlytics disabled. setCollectionEnabled=$enabled")
    }

    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        setCollectionEnabled(enabled)
    }

    fun setUserId(userId: String?) {
        Log.d(TAG, "setUserId ignored: ${userId.orEmpty()}")
    }

    fun setCustomKey(key: String, value: String) {
        Log.d(TAG, "setCustomKey ignored: $key=$value")
    }

    fun setCustomKey(key: String, value: Boolean) {
        Log.d(TAG, "setCustomKey ignored: $key=$value")
    }

    fun setCustomKey(key: String, value: Int) {
        Log.d(TAG, "setCustomKey ignored: $key=$value")
    }

    fun setCustomKey(key: String, value: Long) {
        Log.d(TAG, "setCustomKey ignored: $key=$value")
    }

    fun setCustomKey(key: String, value: Double) {
        Log.d(TAG, "setCustomKey ignored: $key=$value")
    }

    fun log(message: String) {
        Log.d(TAG, message)
    }

    fun logEvent(name: String) {
        Log.d(TAG, "event=$name")
    }

    fun logEvent(name: String, params: Map<String, Any?>) {
        Log.d(TAG, "event=$name params=$params")
    }

    fun logEvent(name: String, params: Bundle) {
        Log.d(TAG, "event=$name bundle=$params")
    }

    fun logEvent(name: String, key: String, value: Any?) {
        Log.d(TAG, "event=$name $key=$value")
    }

    fun logEvent(name: String, vararg pairs: Pair<String, Any?>) {
        Log.d(TAG, "event=$name params=${pairs.toMap()}")
    }

    fun logError(message: String) {
        Log.e(TAG, message)
    }

    fun logError(throwable: Throwable) {
        Log.e(TAG, "error", throwable)
    }

    fun logError(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    fun logError(throwable: Throwable, message: String) {
        Log.e(TAG, message, throwable)
    }

    fun recordException(throwable: Throwable) {
        Log.e(TAG, "recordException", throwable)
    }

    fun recordException(throwable: Throwable, message: String) {
        Log.e(TAG, message, throwable)
    }

    fun logTiming(name: String, durationMs: Long) {
        Log.d(TAG, "timing=$name durationMs=$durationMs")
    }

    fun logTiming(name: String, durationMs: Long, params: Map<String, Any?>) {
        Log.d(TAG, "timing=$name durationMs=$durationMs params=$params")
    }
}
