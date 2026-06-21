package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CancellationException

enum class HermesCodingAssistantMode(val label: String) {
    ANALYSIS("Analyse"),
    CODE_REVIEW("Code Review"),
    PATCH_PROPOSAL("Patch-Vorschlag")
}

const val HERMES_CODING_MAX_IMPORT_BYTES = 200_000L

data class HermesCodingAssistantUiState(
    val input: String = "",
    val selectedMode: HermesCodingAssistantMode = HermesCodingAssistantMode.ANALYSIS,
    val result: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val importWarning: String? = null,
    val importedFileName: String? = null
)

@HiltViewModel
class HermesCodingAssistantViewModel @Inject constructor(
    private val apiManager: ApiManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HermesCodingAssistantUiState())
    val uiState: StateFlow<HermesCodingAssistantUiState> = _uiState.asStateFlow()

    fun updateInput(input: String) {
        _uiState.value = _uiState.value.copy(input = input)
    }

    fun selectMode(mode: HermesCodingAssistantMode) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun importTextFile(fileName: String, content: String) {
        val (redactedContent, redactionCount) = redactSecrets(content)
        val existingInput = _uiState.value.input.trimEnd()
        val importedBlock = buildString {
            if (existingInput.isNotBlank()) appendLine()
            appendLine()
            appendLine("--- Importierte Textdatei: $fileName ---")
            appendLine(redactedContent.trim())
        }.trimStart()
        val warning = buildString {
            append("Textdatei importiert. Bitte prüfe den Inhalt vor dem Analysieren.")
            if (redactionCount > 0) {
                append(" $redactionCount mögliche Secrets wurden maskiert.")
            }
        }
        _uiState.value = _uiState.value.copy(
            input = existingInput + importedBlock,
            result = null,
            error = null,
            importWarning = warning,
            importedFileName = fileName
        )
    }

    fun rejectImportedFile(message: String) {
        _uiState.value = _uiState.value.copy(
            result = null,
            error = message,
            importWarning = null,
            importedFileName = null
        )
    }

    fun analyze() {
        val state = _uiState.value
        if (state.isLoading) return

        val trimmedInput = state.input.trim()
        if (trimmedInput.isBlank()) {
            _uiState.value = state.copy(
                result = null,
                error = "Bitte füge zuerst Code, eine Datei-Beschreibung oder eine konkrete Frage ein."
            )
            return
        }

        val prompt = HermesCodingPromptBuilder.buildPrompt(
            mode = state.selectedMode,
            userInput = trimmedInput
        )
        _uiState.value = state.copy(
            isLoading = true,
            error = null,
            result = null
        )

        viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(45_000L) {
                    apiManager.generateReply(prompt.systemPrompt, prompt.userPrompt)
                }
                when {
                    response == null -> {
                        _uiState.value = _uiState.value.copy(
                            error = "Die Analyse hat zu lange gedauert. Bitte später erneut versuchen."
                        )
                    }

                    response.success && response.content.isNotBlank() -> {
                        _uiState.value = _uiState.value.copy(result = response.content.trim())
                    }

                    else -> {
                        _uiState.value = _uiState.value.copy(
                            error = "Kein KI-Anbieter erreichbar. Bitte API-Schlüssel und Modell in den Einstellungen prüfen."
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Analyse fehlgeschlagen. Bitte API-Einstellungen prüfen und erneut versuchen."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun redactSecrets(text: String): Pair<String, Int> {
        var redactionCount = 0
        val patterns = listOf(
            Regex("(?i)(api[_-]?key\\s*[:=]\\s*)[^\\s\"']+"),
            Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\"']+"),
            Regex("(?i)(token\\s*[:=]\\s*)[^\\s\"']+"),
            Regex("(?i)(password\\s*[:=]\\s*)[^\\s\"']+"),
            Regex("(?i)(secret\\s*[:=]\\s*)[^\\s\"']+")
        )
        val redacted = patterns.fold(text) { current, regex ->
            regex.replace(current) { match ->
                redactionCount += 1
                match.groupValues[1] + "[MASKIERT]"
            }
        }
        return redacted to redactionCount
    }
}
