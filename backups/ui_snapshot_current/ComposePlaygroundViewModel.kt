package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class PlaygroundFlowUiState(
    val uptimeSeconds: Int = 0,
    val isOnline: Boolean = true,
    val queuedTasks: Int = 0
) {
    val statusText: String
        get() = if (isOnline) "Online" else "Offline"
}

class ComposePlaygroundViewModel : ViewModel() {
    sealed interface PlaygroundEvent {
        data class ShowSnackbar(val message: String) : PlaygroundEvent
        data class NavigateToArgumentDemo(val demoId: Int) : PlaygroundEvent
    }

    private val uptimeSeconds = MutableStateFlow(0)
    private val online = MutableStateFlow(true)
    private val queuedTasks = MutableStateFlow(0)
    private val _events = MutableSharedFlow<PlaygroundEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaygroundEvent> = _events.asSharedFlow()

    val uiState: StateFlow<PlaygroundFlowUiState> = combine(
        uptimeSeconds,
        online,
        queuedTasks
    ) { uptime, isOnline, tasks ->
        PlaygroundFlowUiState(
            uptimeSeconds = uptime,
            isOnline = isOnline,
            queuedTasks = tasks
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaygroundFlowUiState()
    )

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                uptimeSeconds.value = uptimeSeconds.value + 1
            }
        }
    }

    fun toggleOnline() {
        online.value = !online.value
        val statusText = if (online.value) "Online-Modus aktiv" else "Offline-Modus aktiv"
        _events.tryEmit(PlaygroundEvent.ShowSnackbar(statusText))
    }

    fun addTask() {
        queuedTasks.value = queuedTasks.value + 1
        _events.tryEmit(PlaygroundEvent.ShowSnackbar("Task hinzugefügt (${queuedTasks.value})"))
    }

    fun clearTasks() {
        queuedTasks.value = 0
        _events.tryEmit(PlaygroundEvent.ShowSnackbar("Queue geleert"))
    }

    fun emitInfoEvent() {
        _events.tryEmit(PlaygroundEvent.ShowSnackbar("Info-Event vom ViewModel"))
    }

    fun emitWarningEvent() {
        _events.tryEmit(PlaygroundEvent.ShowSnackbar("Warn-Event: Prüfe deine Eingaben"))
    }

    fun emitNavigateEvent(demoId: Int) {
        _events.tryEmit(PlaygroundEvent.NavigateToArgumentDemo(demoId))
    }
}
