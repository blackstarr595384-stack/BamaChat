package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.service.GitHubProposalAnalysisIssue
import com.example.bamachat.service.GitHubProposalAnalysisResult
import com.example.bamachat.service.GitHubProposalAnalyzer
import com.example.bamachat.shared.core.github.GitHubAnalysisArea
import com.example.bamachat.shared.core.github.GitHubImprovementProposal
import com.example.bamachat.shared.core.github.GitHubReadIssue
import com.example.bamachat.shared.core.github.GitHubReadOnlyRepositoryGateway
import com.example.bamachat.shared.core.github.GitHubReadResult
import com.example.bamachat.shared.core.github.GitHubRepositoryPolicy
import com.example.bamachat.shared.core.github.RepositoryContextBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GitHubIntelligencePhase {
    IDLE,
    LOADING_REPOSITORY,
    BUILDING_CONTEXT,
    ANALYZING,
    SUCCESS,
    NO_RESULTS,
    ERROR,
    CANCELLED
}

data class GitHubSnapshotSummaryUi(
    val resolvedRef: String,
    val headCommitSha: String,
    val treeEntryCount: Int,
    val selectedFileCount: Int,
    val selectedPaths: List<String>,
    val truncated: Boolean
)

data class GitHubIntelligenceUiState(
    val repositoryOwner: String = GitHubRepositoryPolicy.OWNER,
    val repositoryName: String = GitHubRepositoryPolicy.REPOSITORY,
    val selectedRef: String = GitHubRepositoryPolicy.DEFAULT_REF,
    val selectedArea: GitHubAnalysisArea? = null,
    val phase: GitHubIntelligencePhase = GitHubIntelligencePhase.IDLE,
    val snapshotSummary: GitHubSnapshotSummaryUi? = null,
    val proposals: List<GitHubImprovementProposal> = emptyList(),
    val errorMessage: String? = null
) {
    val analysisInProgress: Boolean
        get() = phase == GitHubIntelligencePhase.LOADING_REPOSITORY ||
            phase == GitHubIntelligencePhase.BUILDING_CONTEXT ||
            phase == GitHubIntelligencePhase.ANALYZING

    val canStart: Boolean
        get() = selectedArea != null && !analysisInProgress
}

@HiltViewModel
class GitHubIntelligenceViewModel @Inject constructor(
    private val repositoryGateway: GitHubReadOnlyRepositoryGateway,
    private val proposalAnalyzer: GitHubProposalAnalyzer,
    private val contextBuilder: RepositoryContextBuilder
) : ViewModel() {
    private val _uiState = MutableStateFlow(GitHubIntelligenceUiState())
    val uiState: StateFlow<GitHubIntelligenceUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun selectArea(area: GitHubAnalysisArea) {
        _uiState.update { state ->
            if (state.analysisInProgress || state.selectedArea == area) {
                state
            } else {
                state.copy(
                    selectedArea = area,
                    phase = GitHubIntelligencePhase.IDLE,
                    snapshotSummary = null,
                    proposals = emptyList(),
                    errorMessage = null
                )
            }
        }
    }

    fun selectRef(ref: String) {
        if (!GitHubRepositoryPolicy.isAllowedRef(ref)) return
        _uiState.update { state ->
            if (state.analysisInProgress || state.selectedRef == ref) {
                state
            } else {
                state.copy(
                    selectedRef = ref,
                    phase = GitHubIntelligencePhase.IDLE,
                    snapshotSummary = null,
                    proposals = emptyList(),
                    errorMessage = null
                )
            }
        }
    }

    fun startAnalysis() {
        if (analysisJob?.isActive == true) return
        val startState = _uiState.value
        val area = startState.selectedArea
        if (area == null) {
            _uiState.update {
                it.copy(
                    phase = GitHubIntelligencePhase.ERROR,
                    errorMessage = "Bitte zuerst einen Analysebereich auswählen."
                )
            }
            return
        }
        analysisJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        phase = GitHubIntelligencePhase.LOADING_REPOSITORY,
                        errorMessage = null
                    )
                }
                val snapshot = when (
                    val result = repositoryGateway.readSnapshot(
                        repository = GitHubRepositoryPolicy.repository,
                        ref = startState.selectedRef,
                        analysisArea = area
                    )
                ) {
                    is GitHubReadResult.Success -> result.value
                    is GitHubReadResult.Failure -> {
                        finishWithRepositoryError(result)
                        return@launch
                    }
                }
                val summary = GitHubSnapshotSummaryUi(
                    resolvedRef = snapshot.resolvedRef,
                    headCommitSha = snapshot.headCommitSha,
                    treeEntryCount = snapshot.treeEntries.size,
                    selectedFileCount = snapshot.selectedFiles.size,
                    selectedPaths = snapshot.selectedFiles.map { it.path }.sorted(),
                    truncated = snapshot.truncationInformation.truncated
                )
                _uiState.update {
                    it.copy(
                        phase = GitHubIntelligencePhase.BUILDING_CONTEXT,
                        snapshotSummary = summary,
                        errorMessage = null
                    )
                }
                val context = contextBuilder.build(snapshot)
                if (context.includedPaths.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            phase = GitHubIntelligencePhase.ERROR,
                            errorMessage = "Für diesen Analysebereich wurden keine freigegebenen Textdateien gefunden."
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        phase = GitHubIntelligencePhase.ANALYZING,
                        errorMessage = null
                    )
                }
                when (val result = proposalAnalyzer.analyze(context, area)) {
                    is GitHubProposalAnalysisResult.Success -> {
                        _uiState.update {
                            it.copy(
                                phase = GitHubIntelligencePhase.SUCCESS,
                                proposals = result.proposals,
                                errorMessage = null
                            )
                        }
                    }
                    GitHubProposalAnalysisResult.NoActionableProposals -> {
                        _uiState.update {
                            it.copy(
                                phase = GitHubIntelligencePhase.NO_RESULTS,
                                proposals = emptyList(),
                                errorMessage = null
                            )
                        }
                    }
                    is GitHubProposalAnalysisResult.Failure -> finishWithAnalysisError(result.issue)
                }
            } catch (cancelled: CancellationException) {
                _uiState.update {
                    it.copy(
                        phase = GitHubIntelligencePhase.CANCELLED,
                        errorMessage = null
                    )
                }
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        phase = GitHubIntelligencePhase.ERROR,
                        errorMessage = "Die GitHub-Analyse konnte nicht abgeschlossen werden."
                    )
                }
            } finally {
                analysisJob = null
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.takeIf { it.isActive }?.cancel()
    }

    private fun finishWithRepositoryError(failure: GitHubReadResult.Failure) {
        _uiState.update {
            it.copy(
                phase = GitHubIntelligencePhase.ERROR,
                errorMessage = repositoryErrorMessage(failure)
            )
        }
    }

    private fun finishWithAnalysisError(issue: GitHubProposalAnalysisIssue) {
        val message = when (issue) {
            GitHubProposalAnalysisIssue.AI_UNAVAILABLE ->
                "Für die Analyse ist derzeit kein KI-Anbieter verfügbar."
            GitHubProposalAnalysisIssue.EMPTY_RESPONSE,
            GitHubProposalAnalysisIssue.INVALID_JSON,
            GitHubProposalAnalysisIssue.MISSING_REQUIRED_FIELDS,
            GitHubProposalAnalysisIssue.UNKNOWN_EVIDENCE_PATH,
            GitHubProposalAnalysisIssue.AMBIGUOUS_JSON_PAYLOAD,
            GitHubProposalAnalysisIssue.RESPONSE_TOO_LARGE ->
                "Die KI-Antwort enthielt keine gültigen strukturierten Vorschläge."
            GitHubProposalAnalysisIssue.REQUEST_FAILED ->
                "Die KI-Analyse konnte nicht abgeschlossen werden."
        }
        _uiState.update {
            it.copy(
                phase = GitHubIntelligencePhase.ERROR,
                errorMessage = message
            )
        }
    }

    private fun repositoryErrorMessage(failure: GitHubReadResult.Failure): String = when (failure.issue) {
        GitHubReadIssue.REPOSITORY_NOT_ALLOWED,
        GitHubReadIssue.REF_NOT_ALLOWED,
        GitHubReadIssue.PATH_NOT_ALLOWED ->
            "Der angeforderte GitHub-Inhalt ist nicht freigegeben."
        GitHubReadIssue.NOT_FOUND ->
            "Der freigegebene GitHub-Inhalt wurde nicht gefunden."
        GitHubReadIssue.RATE_LIMITED ->
            GitHubRateLimitMessageFormatter.message(failure.rateLimitResetEpochSeconds)
        GitHubReadIssue.ACCESS_DENIED ->
            "GitHub hat den öffentlichen Lesezugriff abgelehnt."
        GitHubReadIssue.REDIRECT_BLOCKED ->
            "GitHub hat auf ein nicht freigegebenes Ziel weitergeleitet."
        GitHubReadIssue.NETWORK_REQUEST_BUDGET_EXHAUSTED ->
            "Das sichere GitHub-Abruflimit für diese Analyse ist erreicht."
        GitHubReadIssue.REQUEST_TIMED_OUT ->
            "GitHub hat nicht rechtzeitig geantwortet."
        GitHubReadIssue.NETWORK_UNAVAILABLE,
        GitHubReadIssue.SERVICE_UNAVAILABLE ->
            "GitHub ist derzeit nicht erreichbar."
        GitHubReadIssue.RESPONSE_TOO_LARGE ->
            "Der GitHub-Inhalt überschreitet das sichere Größenlimit."
        GitHubReadIssue.INVALID_RESPONSE,
        GitHubReadIssue.UNSUPPORTED_ENCODING ->
            "Der GitHub-Inhalt konnte nicht sicher gelesen werden."
    }
}

internal object GitHubRateLimitMessageFormatter {
    private const val GENERIC_MESSAGE =
        "Das öffentliche GitHub-Leselimit ist erreicht. Bitte später erneut versuchen."
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

    fun message(
        resetEpochSeconds: Long?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (resetEpochSeconds == null || resetEpochSeconds <= 0L) return GENERIC_MESSAGE
        val localTime = runCatching {
            Instant.ofEpochSecond(resetEpochSeconds)
                .atZone(zoneId)
                .format(timeFormatter)
        }.getOrNull() ?: return GENERIC_MESSAGE
        return "Das öffentliche GitHub-Leselimit ist erreicht.\n" +
            "Neuer Versuch voraussichtlich ab $localTime Uhr."
    }
}
