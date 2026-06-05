package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentState(
    val goal: String = "",
    val isRunning: Boolean = false,
    val steps: List<AgentStep> = emptyList(),
    val currentStep: Int = 0,
    val result: String? = null,
    val error: String? = null
)

data class AgentStep(
    val id: Int,
    val description: String,
    val status: StepStatus,
    val output: String? = null,
    val toolUsed: String? = null
)

enum class StepStatus { PENDING, RUNNING, COMPLETED, FAILED }

@HiltViewModel
class AgentNotebookViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private var executionJob: Job? = null

    fun setGoal(goal: String) {
        _state.value = _state.value.copy(goal = goal)
    }

    fun startAgent() {
        val goal = _state.value.goal.trim()
        if (goal.isBlank()) {
            _state.value = _state.value.copy(error = "Bitte gib ein Ziel ein.")
            return
        }

        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            val simulatedSteps = listOf(
                AgentStep(1, "Analysiere die Anforderung: \"$goal\"", StepStatus.PENDING, toolUsed = "\uD83E\uDD16"),
                AgentStep(2, "Durchsuche relevante Wissensquellen und Kontext", StepStatus.PENDING, toolUsed = "\uD83D\uDD0D"),
                AgentStep(3, "Verarbeite und strukturiere die Informationen", StepStatus.PENDING, toolUsed = "\uD83D\uDCCA"),
                AgentStep(4, "Führe Berechnungen und Überprüfungen durch", StepStatus.PENDING, toolUsed = "\u2699\uFE0F"),
                AgentStep(5, "Erstelle die finale Ausgabe und Zusammenfassung", StepStatus.PENDING, toolUsed = "\u270D\uFE0F")
            )

            val steps = simulatedSteps.toMutableList()
            _state.value = _state.value.copy(
                isRunning = true,
                steps = steps,
                currentStep = 0,
                result = null,
                error = null
            )

            for (i in steps.indices) {
                steps[i] = steps[i].copy(status = StepStatus.RUNNING)
                _state.value = _state.value.copy(steps = steps.toList(), currentStep = i)

                delay((1200L + 300L * i))

                val outputs = listOf(
                    "Anforderung erfasst: $goal",
                    "3 relevante Quellen gefunden und analysiert",
                    "Informationen in Kategorien strukturiert",
                    "Daten validiert und auf Konsistenz geprüft",
                    "Zusammenfassung mit ${i + 1} Kernpunkten erstellt"
                )

                val stepResult = if (i == 2) null else outputs[i]

                if (i < steps.size - 1 && i == 2) {
                    steps[i] = steps[i].copy(status = StepStatus.COMPLETED, output = outputs[i])
                    _state.value = _state.value.copy(steps = steps.toList())
                    delay(500)

                    val extraStep = AgentStep(
                        id = steps.size + 1,
                        description = "Zusätzliche Prüfung der Quellen auf Aktualität",
                        status = StepStatus.RUNNING,
                        toolUsed = "\uD83D\uDCC5"
                    )
                    steps.add(extraStep)
                    _state.value = _state.value.copy(steps = steps.toList(), currentStep = i + 1)
                    delay(1000)
                    steps[steps.size - 1] = steps[steps.size - 1].copy(
                        status = StepStatus.COMPLETED,
                        output = "Alle Quellen sind aktuell (Stand: diese Woche)"
                    )
                    _state.value = _state.value.copy(steps = steps.toList())
                    delay(300)
                    continue
                }

                steps[i] = steps[i].copy(status = StepStatus.COMPLETED, output = stepResult)
                _state.value = _state.value.copy(steps = steps.toList())
            }

            val allCompleted = steps.all { it.status == StepStatus.COMPLETED }
            if (allCompleted) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    result = buildString {
                        appendLine("## Ziel erreicht: $goal")
                        appendLine()
                        appendLine("Der Agent hat alle Schritte erfolgreich abgeschlossen.")
                        appendLine()
                        appendLine("### Zusammenfassung")
                        appendLine("- Anforderung analysiert und verstanden")
                        appendLine("- Relevante Quellen identifiziert und ausgewertet")
                        appendLine("- Informationen strukturiert aufbereitet")
                        appendLine("- Qualitätsprüfung durchgeführt")
                        appendLine("- Finales Ergebnis erstellt")
                    }
                )
            }
        }
    }

    fun stopAgent() {
        executionJob?.cancel()
        val currentSteps = _state.value.steps.map { step ->
            if (step.status == StepStatus.RUNNING) step.copy(status = StepStatus.FAILED)
            else step
        }
        _state.value = _state.value.copy(
            isRunning = false,
            steps = currentSteps,
            result = null,
            error = "Agent wurde manuell gestoppt."
        )
    }

    fun reset() {
        executionJob?.cancel()
        _state.value = AgentState()
    }
}
