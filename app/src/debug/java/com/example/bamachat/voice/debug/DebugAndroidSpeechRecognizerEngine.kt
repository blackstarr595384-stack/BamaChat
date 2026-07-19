package com.example.bamachat.voice.debug

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DebugAndroidSpeechRecognizerEngine(context: Context) : LocalHardwareSpeechRecognizer {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var currentListener: LocalHardwareRecognitionListener? = null
    private var finalDelivered = false
    private var released = false

    override fun isAvailable(): Boolean = !released && SpeechRecognizer.isRecognitionAvailable(appContext)

    override suspend fun start(
        listener: LocalHardwareRecognitionListener
    ): LocalAudioOperationResult = withContext(Dispatchers.Main.immediate) {
        if (!isAvailable()) {
            return@withContext LocalAudioOperationResult.Failure(
                LocalAudioFailure(
                    LocalAudioErrorCategory.RECOGNIZER_UNAVAILABLE,
                    "Android-Spracherkennung ist auf diesem Gerät nicht verfügbar."
                )
            )
        }

        runCatching { recognizer?.cancel() }
        currentListener = listener
        finalDelivered = false
        val activeRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            recognizer = it
        }
        activeRecognizer.setRecognitionListener(createRecognitionListener())
        try {
            activeRecognizer.startListening(createRecognizerIntent())
            LocalAudioOperationResult.Success
        } catch (_: SecurityException) {
            currentListener = null
            LocalAudioOperationResult.Failure(permissionFailure())
        } catch (_: Throwable) {
            currentListener = null
            LocalAudioOperationResult.Failure(
                LocalAudioFailure(
                    LocalAudioErrorCategory.TEMPORARY,
                    "Android-Spracherkennung konnte nicht gestartet werden."
                )
            )
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        runCatching { recognizer?.stopListening() }
        Unit
    }

    override suspend fun cancel() = withContext(Dispatchers.Main.immediate) {
        currentListener = null
        finalDelivered = false
        destroyRecognizer(cancelFirst = true)
        Unit
    }

    override suspend fun release() = withContext(Dispatchers.Main.immediate) {
        if (released) return@withContext
        released = true
        currentListener = null
        finalDelivered = false
        destroyRecognizer(cancelFirst = true)
    }

    private fun createRecognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_TAG)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE_TAG)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            currentListener?.onReady()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            val listener = currentListener ?: return
            currentListener = null
            finalDelivered = false
            destroyRecognizer(cancelFirst = false)
            listener.onFailure(mapRecognitionError(error))
        }

        override fun onResults(results: Bundle?) {
            val listener = currentListener ?: return
            if (finalDelivered) return
            finalDelivered = true
            currentListener = null
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            destroyRecognizer(cancelFirst = false)
            if (text.isBlank()) {
                listener.onFailure(
                    LocalAudioFailure(
                        LocalAudioErrorCategory.NO_SPEECH,
                        "Es wurde kein verständlicher Spracheingang erkannt."
                    )
                )
            } else {
                listener.onFinalTranscript(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            if (text.isNotBlank()) currentListener?.onPartialTranscript(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun mapRecognitionError(error: Int): LocalAudioFailure = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> permissionFailure()
        SpeechRecognizer.ERROR_NETWORK -> LocalAudioFailure(
            LocalAudioErrorCategory.NETWORK,
            "Der Android-Systemdienst für Spracherkennung hat keine Verbindung."
        )
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> LocalAudioFailure(
            LocalAudioErrorCategory.TIMEOUT,
            "Die Android-Spracherkennung hat das Zeitlimit erreicht."
        )
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_NO_MATCH -> LocalAudioFailure(
            LocalAudioErrorCategory.NO_SPEECH,
            "Es wurde kein verständlicher Spracheingang erkannt."
        )
        SpeechRecognizer.ERROR_CLIENT -> LocalAudioFailure(
            LocalAudioErrorCategory.CANCELLED,
            "Die Spracheingabe wurde beendet."
        )
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> LocalAudioFailure(
            LocalAudioErrorCategory.TEMPORARY,
            "Die Android-Spracherkennung ist noch beschäftigt."
        )
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> LocalAudioFailure(
            LocalAudioErrorCategory.TEMPORARY,
            "Die Android-Spracherkennung wurde zu oft gestartet. Bitte kurz warten."
        )
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> LocalAudioFailure(
            LocalAudioErrorCategory.RECOGNIZER_UNAVAILABLE,
            "Deutsch ist für die aktuelle Android-Spracherkennung nicht verfügbar."
        )
        else -> LocalAudioFailure(
            LocalAudioErrorCategory.TEMPORARY,
            "Die Android-Spracherkennung wurde unerwartet beendet."
        )
    }

    private fun permissionFailure() = LocalAudioFailure(
        LocalAudioErrorCategory.PERMISSION_MISSING,
        "Mikrofonzugriff ist nicht erlaubt."
    )

    private fun destroyRecognizer(cancelFirst: Boolean) {
        val activeRecognizer = recognizer ?: return
        recognizer = null
        if (cancelFirst) runCatching { activeRecognizer.cancel() }
        runCatching { activeRecognizer.destroy() }
    }

    companion object {
        private const val LANGUAGE_TAG = "de-DE"
    }
}
