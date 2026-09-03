package com.example.bamachat.voice.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.bamachat.voice.android.AndroidTextToSpeechEngine
import com.example.bamachat.voice.android.AndroidVoiceAudioSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@HiltViewModel
class DebugAudioHardwareViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller = LocalAudioHardwareTestController(
        recognizer = DebugAndroidSpeechRecognizerEngine(context),
        speechOutput = AndroidTextToSpeechEngine(context),
        audioSession = AndroidVoiceAudioSession(context),
        scope = controllerScope
    )

    val uiState = controller.uiState

    fun startMicrophone(permissionGranted: Boolean) = runAction {
        controller.startMicrophone(permissionGranted)
    }

    fun stopMicrophone() = runAction { controller.stopMicrophone() }

    fun startSpeechTest() = runAction { controller.startSpeechTest() }

    fun stopSpeechOutput() = runAction { controller.stopSpeechOutput() }

    fun startLocalConversation(permissionGranted: Boolean) = runAction {
        controller.startLocalConversation(permissionGranted)
    }

    fun interruptAndListen(permissionGranted: Boolean) = runAction {
        controller.interruptAndListen(permissionGranted)
    }

    fun endConversation() = runAction { controller.endConversation() }

    fun stopForLifecycle() = runAction { controller.stopForLifecycle() }

    fun reportPermissionDenied() = controller.reportPermissionDenied()

    fun setHandsFree(enabled: Boolean) = controller.setHandsFree(enabled)

    fun setSpeechSpeed(speed: Float) = controller.setSpeechSpeed(speed)

    fun setSpeechPitch(pitch: Float) = controller.setSpeechPitch(pitch)

    fun clearDiagnostics() = controller.clearDiagnostics()

    override fun onCleared() {
        controllerScope.launch {
            controller.release()
            controllerScope.cancel()
        }
        super.onCleared()
    }

    private fun runAction(action: suspend () -> Unit) {
        controllerScope.launch { action() }
    }
}
