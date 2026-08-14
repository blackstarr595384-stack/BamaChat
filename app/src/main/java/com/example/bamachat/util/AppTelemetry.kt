package com.example.bamachat.util

import android.content.Context
import android.os.Bundle
import android.util.Log

object AppTelemetry {
    private const val TAG = "BamaChatTelemetry"
    private val safeName = Regex("[A-Za-z0-9_.-]{1,80}")
    private val sensitiveKey = Regex(
        "(?i).*(uid|email|query|message|text|token|secret|key|password|authorization|credential).*"
    )
    @Volatile private var testSink: ((String) -> Unit)? = null

    fun initialize(collectionEnabled: Boolean = false) {
        debug("telemetry_initialized collectionEnabled=$collectionEnabled")
    }

    fun initialize(context: Context, collectionEnabled: Boolean = false) {
        debug("telemetry_initialized package=${safeToken(context.packageName)} collectionEnabled=$collectionEnabled")
    }

    fun setCollectionEnabled(enabled: Boolean) {
        debug("crashlytics_disabled collectionEnabled=$enabled")
    }

    fun setCrashlyticsCollectionEnabled(enabled: Boolean) = setCollectionEnabled(enabled)

    fun setUserId(userId: String?) {
        debug("user_identity_ignored state=${if (userId.isNullOrBlank()) "cleared" else "present"}")
    }

    fun setCustomKey(key: String, value: String) =
        debug("custom_key_ignored ${safeToken(key)}=${safeValue(key, value)}")

    fun setCustomKey(key: String, value: Boolean) = debug("custom_key_ignored ${safeToken(key)}=$value")
    fun setCustomKey(key: String, value: Int) = debug("custom_key_ignored ${safeToken(key)}=$value")
    fun setCustomKey(key: String, value: Long) = debug("custom_key_ignored ${safeToken(key)}=$value")
    fun setCustomKey(key: String, value: Double) = debug("custom_key_ignored ${safeToken(key)}=$value")

    fun log(message: String) {
        debug("diagnostic_message_received length=${message.length.coerceAtMost(10_000)}")
    }

    fun logEvent(name: String) = debug("event=${safeToken(name)}")

    fun logEvent(name: String, params: Map<String, Any?>) =
        debug("event=${safeToken(name)} params=${safeParams(params)}")

    fun logEvent(name: String, params: Bundle) {
        val values = params.keySet().associateWith { key -> params.get(key) }
        logEvent(name, values)
    }

    fun logEvent(name: String, key: String, value: Any?) = logEvent(name, mapOf(key to value))

    fun logEvent(name: String, vararg pairs: Pair<String, Any?>) = logEvent(name, pairs.toMap())

    fun logError(message: String) = error("error=${safeToken(message)}")

    fun logError(throwable: Throwable) = error("error=uncategorized type=${safeErrorType(throwable)}")

    fun logError(message: String, throwable: Throwable?) =
        error("error=${safeToken(message)} type=${throwable?.let(::safeErrorType) ?: "none"}")

    fun logError(throwable: Throwable, message: String) = logError(message, throwable)

    fun recordException(throwable: Throwable) =
        error("error=recorded_exception type=${safeErrorType(throwable)}")

    fun recordException(throwable: Throwable, message: String) = logError(message, throwable)

    fun logTiming(name: String, durationMs: Long) {
        debug("timing=${safeToken(name)} durationMs=${durationMs.coerceAtLeast(0)}")
    }

    fun logTiming(name: String, durationMs: Long, params: Map<String, Any?>) {
        debug(
            "timing=${safeToken(name)} durationMs=${durationMs.coerceAtLeast(0)} " +
                "params=${safeParams(params)}"
        )
    }

    internal fun installTestSink(sink: ((String) -> Unit)?) {
        testSink = sink
    }

    private fun safeParams(params: Map<String, Any?>): String = params.entries
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${safeToken(key)}=${safeValue(key, value)}"
        }

    private fun safeValue(key: String, value: Any?): String = when {
        sensitiveKey.matches(key) -> "redacted"
        value == null -> "null"
        value is Boolean || value is Byte || value is Short || value is Int ||
            value is Long || value is Float || value is Double -> value.toString()
        value is Enum<*> -> safeToken(value.name)
        else -> "redacted_length=${value.toString().length.coerceAtMost(10_000)}"
    }

    private fun safeToken(value: String): String = value.trim().takeIf(safeName::matches) ?: "redacted"

    private fun safeErrorType(throwable: Throwable): String =
        safeToken(throwable::class.java.simpleName.ifBlank { "Throwable" })

    private fun debug(message: String) {
        testSink?.invoke("DEBUG:$message") ?: Log.d(TAG, message)
    }

    private fun error(message: String) {
        testSink?.invoke("ERROR:$message") ?: Log.e(TAG, message)
    }
}
