package com.example.bamachat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.BuildConfig
import com.example.bamachat.service.GitHubProposalAnalysisIssue
import com.example.bamachat.service.GitHubProposalAnalysisResult
import com.example.bamachat.service.GitHubProposalAnalyzer
import com.example.bamachat.shared.core.github.AgentDraftPrGateway
import com.example.bamachat.shared.core.github.AgentDraftPrGatewayResult
import com.example.bamachat.shared.core.github.AgentDraftPrIssue
import com.example.bamachat.shared.core.github.AgentDraftPrProposalEligibilityPolicy
import com.example.bamachat.shared.core.github.AgentDraftPrProposalSelection
import com.example.bamachat.shared.core.github.AgentDraftPrProposalSelectionFactory
import com.example.bamachat.shared.core.github.AgentDraftPrRequestFactory
import com.example.bamachat.shared.core.github.AgentDraftPrRequestResult
import com.example.bamachat.shared.core.github.AgentDraftPrStatus
import com.example.bamachat.shared.core.github.AgentDraftPrUrlPolicy
import com.example.bamachat.shared.core.github.AgentImplementationPlan
import com.example.bamachat.shared.core.github.AgentImplementationPlanFactory
import com.example.bamachat.shared.core.github.AgentImplementationPlanResult
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

enum class AgentDraftPrUiPhase {
    NONE,
    PLAN_READY,
    SUBMITTING,
    CANCELLING,
    SERVER_ACCEPTED,
    DRAFT_PR_CREATED,
    CANCELLED,
    ERROR
}

data class AgentDraftPrUiState(
    val serverAvailable: Boolean = false,
    val phase: AgentDraftPrUiPhase = AgentDraftPrUiPhase.NONE,
    val activeProposalId: String? = null,
    val selection: AgentDraftPrProposalSelection? = null,
    val plan: AgentImplementationPlan? = null,
    val explicitApprovalChecked: Boolean = false,
    val requestId: String? = null,
    val serverStatus: AgentDraftPrStatus? = null,
    val draftPullRequestNumber: Long? = null,
    val draftPullRequestUrl: String? = null,
    val safeMessage: String? = null
) {
    val submissionInProgress: Boolean
        get() = phase == AgentDraftPrUiPhase.SUBMITTING

    val cancellationInProgress: Boolean
        get() = phase == AgentDraftPrUiPhase.CANCELLING

    val operationInProgress: Boolean
        get() = submissionInProgress || cancellationInProgress

    val canSubmit: Boolean
        get() = phase == AgentDraftPrUiPhase.PLAN_READY &&
            serverAvailable &&
            plan != null &&
            explicitApprovalChecked &&
            !submissionInProgress

    val canChangeApproval: Boolean
        get() = phase == AgentDraftPrUiPhase.PLAN_READY
}

data class GitHubIntelligenceUiState(
    val repositoryOwner: String = GitHubRepositoryPolicy.OWNER,
    val repositoryName: String = GitHubRepositoryPolicy.REPOSITORY,
    val selectedRef: String = GitHubRepositoryPolicy.DEFAULT_REF,
    val selectedArea: GitHubAnalysisArea? = null,
    val phase: GitHubIntelligencePhase = GitHubIntelligencePhase.IDLE,
    val snapshotSummary: GitHubSnapshotSummaryUi? = null,
    val proposals: List<GitHubImprovementProposal> = emptyList(),
    val errorMessage: String? = null,
    val draftPr: AgentDraftPrUiState = AgentDraftPrUiState()
) {
    val analysisInProgress: Boolean
        get() = phase == GitHubIntelligencePhase.LOADING_REPOSITORY ||
            phase == GitHubIntelligencePhase.BUILDING_CONTEXT ||
            phase == GitHubIntelligencePhase.ANALYZING

    val canStart: Boolean
        get() = selectedArea != null && !analysisInProgress && !draftPr.operationInProgress
}

@HiltViewModel
class GitHubIntelligenceViewModel @Inject constructor(
    private val repositoryGateway: GitHubReadOnlyRepositoryGateway,
    private val proposalAnalyzer: GitHubProposalAnalyzer,
    private val contextBuilder: RepositoryContextBuilder,
    private val agentDraftPrGateway: AgentDraftPrGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        GitHubIntelligenceUiState(
            draftPr = AgentDraftPrUiState(serverAvailable = agentDraftPrGateway.serverAvailable)
        )
    )
    val uiState: StateFlow<GitHubIntelligenceUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null
    private var draftPrJob: Job? = null
    private var draftPrCancellationJob: Job? = null
    private var approvedPlanBinding: String? = null

    fun selectArea(area: GitHubAnalysisArea) {
        _uiState.update { state ->
            if (state.analysisInProgress ||
                state.draftPr.operationInProgress ||
                state.selectedArea == area
            ) {
                state
            } else {
                state.copy(
                    selectedArea = area,
                    phase = GitHubIntelligencePhase.IDLE,
                    snapshotSummary = null,
                    proposals = emptyList(),
                    errorMessage = null,
                    draftPr = emptyDraftPrState()
                )
            }
        }
    }

    fun selectRef(ref: String) {
        if (!GitHubRepositoryPolicy.isAllowedRef(ref)) return
        _uiState.update { state ->
            if (state.analysisInProgress ||
                state.draftPr.operationInProgress ||
                state.selectedRef == ref
            ) {
                state
            } else {
                state.copy(
                    selectedRef = ref,
                    phase = GitHubIntelligencePhase.IDLE,
                    snapshotSummary = null,
                    proposals = emptyList(),
                    errorMessage = null,
                    draftPr = emptyDraftPrState()
                )
            }
        }
    }

    fun startAnalysis() {
        if (analysisJob?.isActive == true) return
        val startState = _uiState.value
        if (startState.draftPr.operationInProgress) return
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
                        errorMessage = null,
                        draftPr = emptyDraftPrState()
                    )
                }
                approvedPlanBinding = null
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

    fun prepareImplementationPlan(proposalId: String) {
        val state = _uiState.value
        if (state.analysisInProgress || state.draftPr.operationInProgress) return
        val proposal = state.proposals.firstOrNull { it.id == proposalId } ?: return
        val summary = state.snapshotSummary ?: return
        val availablePaths = summary.selectedPaths.toSet()
        if (AgentDraftPrProposalEligibilityPolicy.validate(proposal, availablePaths) != null) {
            approvedPlanBinding = null
            _uiState.update { current ->
                current.copy(
                    draftPr = AgentDraftPrUiState(
                        serverAvailable = agentDraftPrGateway.serverAvailable,
                        phase = AgentDraftPrUiPhase.ERROR,
                        activeProposalId = proposalId,
                        safeMessage = "Für diesen Vorschlag konnte kein sicherer Umsetzungsplan erstellt werden."
                    )
                )
            }
            return
        }
        val nowEpochSeconds = currentEpochSeconds()
        val selection = AgentDraftPrProposalSelectionFactory.create(
            proposalId = proposal.id,
            sourceRef = summary.resolvedRef,
            sourceCommitSha = summary.headCommitSha,
            evidencePaths = proposal.evidence.map { it.path },
            requestedAt = nowEpochSeconds
        )
        val result = AgentImplementationPlanFactory.create(
            proposal = proposal,
            repository = GitHubRepositoryPolicy.repository,
            baseRef = summary.resolvedRef,
            baseCommitSha = summary.headCommitSha,
            availablePaths = availablePaths,
            nowEpochSeconds = nowEpochSeconds
        )
        approvedPlanBinding = null
        _uiState.update { current ->
            when (result) {
                is AgentImplementationPlanResult.Success -> current.copy(
                    draftPr = AgentDraftPrUiState(
                        serverAvailable = agentDraftPrGateway.serverAvailable,
                        phase = AgentDraftPrUiPhase.PLAN_READY,
                        activeProposalId = proposalId,
                        selection = selection,
                        plan = result.plan
                    )
                )
                is AgentImplementationPlanResult.Failure -> current.copy(
                    draftPr = AgentDraftPrUiState(
                        serverAvailable = agentDraftPrGateway.serverAvailable,
                        phase = AgentDraftPrUiPhase.ERROR,
                        activeProposalId = proposalId,
                        safeMessage = "Für diesen Vorschlag konnte kein sicherer Umsetzungsplan erstellt werden."
                    )
                )
            }
        }
    }

    fun setDraftPrApproval(checked: Boolean) {
        _uiState.update { state ->
            val plan = state.draftPr.plan ?: return@update state
            if (!state.draftPr.canChangeApproval) return@update state
            approvedPlanBinding = if (checked) planBinding(plan) else null
            state.copy(
                draftPr = state.draftPr.copy(
                    explicitApprovalChecked = checked,
                    safeMessage = null
                )
            )
        }
    }

    fun submitDraftPrRequest() {
        if (draftPrJob?.isActive == true) return
        val state = _uiState.value
        if (state.draftPr.phase != AgentDraftPrUiPhase.PLAN_READY) return
        val plan = state.draftPr.plan ?: return
        val allowedPaths = state.snapshotSummary?.selectedPaths?.toSet() ?: return
        if (!agentDraftPrGateway.serverAvailable) {
            _uiState.update {
                it.copy(
                    draftPr = it.draftPr.copy(
                        safeMessage = "Sicherer Agent-Service noch nicht verbunden"
                    )
                )
            }
            return
        }
        if (!state.draftPr.explicitApprovalChecked || approvedPlanBinding != planBinding(plan)) {
            _uiState.update {
                it.copy(
                    draftPr = it.draftPr.copy(
                        safeMessage = "Bitte den aktuellen Umsetzungsplan ausdrücklich freigeben."
                    )
                )
            }
            return
        }
        val request = when (
            val result = AgentDraftPrRequestFactory.create(
                plan = plan,
                allowedPaths = allowedPaths,
                explicitApproval = true,
                clientVersion = BuildConfig.VERSION_NAME,
                nowEpochSeconds = currentEpochSeconds()
            )
        ) {
            is AgentDraftPrRequestResult.Success -> result.request
            is AgentDraftPrRequestResult.Failure -> {
                _uiState.update {
                    it.copy(
                        draftPr = it.draftPr.copy(
                            phase = AgentDraftPrUiPhase.ERROR,
                            safeMessage = draftPrIssueMessage(result.issue)
                        )
                    )
                }
                return
            }
        }
        draftPrJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        draftPr = it.draftPr.copy(
                            phase = AgentDraftPrUiPhase.SUBMITTING,
                            requestId = request.requestId,
                            safeMessage = null
                        )
                    )
                }
                when (val validation = agentDraftPrGateway.validatePlan(plan, allowedPaths)) {
                    is AgentDraftPrGatewayResult.Failure -> {
                        finishDraftPrFailure(validation.issue, request.requestId)
                        return@launch
                    }
                    is AgentDraftPrGatewayResult.Success -> Unit
                }
                when (val result = agentDraftPrGateway.submitDraftPrRequest(request)) {
                    is AgentDraftPrGatewayResult.Success -> {
                        if (result.value.requestId != request.requestId ||
                            result.value.branchName != plan.branchName ||
                            result.value.status !in setOf(
                                AgentDraftPrStatus.SERVER_ACCEPTED,
                                AgentDraftPrStatus.DRAFT_PR_CREATED
                            )
                        ) {
                            finishDraftPrFailure(
                                AgentDraftPrIssue.REQUEST_REJECTED,
                                request.requestId
                            )
                            return@launch
                        }
                        val draftPrCreated = result.value.status ==
                            AgentDraftPrStatus.DRAFT_PR_CREATED
                        if (draftPrCreated && !AgentDraftPrUrlPolicy.isAllowed(
                                result.value.draftPullRequestUrl.orEmpty(),
                                result.value.draftPullRequestNumber
                            )
                        ) {
                            finishDraftPrFailure(
                                AgentDraftPrIssue.REQUEST_REJECTED,
                                request.requestId
                            )
                            return@launch
                        }
                        val phase = if (result.value.status == AgentDraftPrStatus.DRAFT_PR_CREATED) {
                            AgentDraftPrUiPhase.DRAFT_PR_CREATED
                        } else {
                            AgentDraftPrUiPhase.SERVER_ACCEPTED
                        }
                        _uiState.update { current ->
                            if (current.draftPr.phase != AgentDraftPrUiPhase.SUBMITTING ||
                                current.draftPr.requestId != request.requestId
                            ) {
                                current
                            } else {
                                approvedPlanBinding = null
                                current.copy(
                                    draftPr = current.draftPr.copy(
                                        phase = phase,
                                        serverStatus = result.value.status,
                                        draftPullRequestNumber = if (draftPrCreated) {
                                            result.value.draftPullRequestNumber
                                        } else {
                                            null
                                        },
                                        draftPullRequestUrl = if (draftPrCreated) {
                                            result.value.draftPullRequestUrl
                                        } else {
                                            null
                                        },
                                        safeMessage = null
                                    )
                                )
                            }
                        }
                    }
                    is AgentDraftPrGatewayResult.Failure ->
                        finishDraftPrFailure(result.issue, request.requestId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                finishDraftPrFailure(AgentDraftPrIssue.REQUEST_FAILED, request.requestId)
            } finally {
                draftPrJob = null
            }
        }
    }

    fun cancelDraftPrRequest() {
        val state = _uiState.value
        if (state.draftPr.phase == AgentDraftPrUiPhase.SUBMITTING) {
            if (draftPrCancellationJob?.isActive == true) return
            val requestId = state.draftPr.requestId ?: return
            val branchName = state.draftPr.plan?.branchName ?: return
            approvedPlanBinding = null
            _uiState.update { current ->
                if (current.draftPr.phase == AgentDraftPrUiPhase.SUBMITTING &&
                    current.draftPr.requestId == requestId
                ) {
                    current.copy(
                        draftPr = current.draftPr.copy(
                            phase = AgentDraftPrUiPhase.CANCELLING,
                            explicitApprovalChecked = false,
                            safeMessage = null
                        )
                    )
                } else {
                    current
                }
            }
            draftPrJob?.cancel()
            draftPrCancellationJob = viewModelScope.launch {
                try {
                    when (val result = agentDraftPrGateway.cancelDraftPrRequest(requestId)) {
                        is AgentDraftPrGatewayResult.Success -> {
                            if (result.value.requestId != requestId ||
                                result.value.branchName != branchName ||
                                result.value.status != AgentDraftPrStatus.CANCELLED
                            ) {
                                finishDraftPrCancellationFailure(requestId)
                            } else {
                                _uiState.update { current ->
                                    if (current.draftPr.phase == AgentDraftPrUiPhase.CANCELLING &&
                                        current.draftPr.requestId == requestId
                                    ) {
                                        current.copy(
                                            draftPr = current.draftPr.copy(
                                                phase = AgentDraftPrUiPhase.CANCELLED,
                                                serverStatus = AgentDraftPrStatus.CANCELLED,
                                                safeMessage = null
                                            )
                                        )
                                    } else {
                                        current
                                    }
                                }
                            }
                        }
                        is AgentDraftPrGatewayResult.Failure ->
                            finishDraftPrCancellationFailure(requestId)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    finishDraftPrCancellationFailure(requestId)
                } finally {
                    draftPrCancellationJob = null
                }
            }
            return
        }
        if (state.draftPr.phase == AgentDraftPrUiPhase.SERVER_ACCEPTED ||
            state.draftPr.phase == AgentDraftPrUiPhase.DRAFT_PR_CREATED ||
            state.draftPr.phase == AgentDraftPrUiPhase.CANCELLING ||
            state.draftPr.phase == AgentDraftPrUiPhase.CANCELLED ||
            state.draftPr.phase == AgentDraftPrUiPhase.ERROR
        ) {
            return
        }
        approvedPlanBinding = null
        _uiState.update { it.copy(draftPr = emptyDraftPrState()) }
    }

    private fun finishDraftPrFailure(issue: AgentDraftPrIssue, expectedRequestId: String) {
        approvedPlanBinding = null
        _uiState.update { current ->
            if (current.draftPr.phase != AgentDraftPrUiPhase.SUBMITTING ||
                current.draftPr.requestId != expectedRequestId
            ) {
                current
            } else {
                current.copy(
                    draftPr = current.draftPr.copy(
                    phase = AgentDraftPrUiPhase.ERROR,
                    safeMessage = draftPrIssueMessage(issue)
                    )
                )
            }
        }
    }

    private fun finishDraftPrCancellationFailure(expectedRequestId: String) {
        _uiState.update { current ->
            if (current.draftPr.phase != AgentDraftPrUiPhase.CANCELLING ||
                current.draftPr.requestId != expectedRequestId
            ) {
                current
            } else {
                current.copy(
                    draftPr = current.draftPr.copy(
                        phase = AgentDraftPrUiPhase.ERROR,
                        safeMessage =
                            "Der Draft-PR-Auftrag konnte nicht sicher abgebrochen werden."
                    )
                )
            }
        }
    }

    private fun draftPrIssueMessage(issue: AgentDraftPrIssue): String = when (issue) {
        AgentDraftPrIssue.SERVER_NOT_CONNECTED -> "Sicherer Agent-Service noch nicht verbunden"
        AgentDraftPrIssue.PLAN_INVALID -> "Der Umsetzungsplan erfüllt die Sicherheitsgrenzen nicht."
        AgentDraftPrIssue.APPROVAL_REQUIRED -> "Bitte den aktuellen Umsetzungsplan ausdrücklich freigeben."
        AgentDraftPrIssue.REQUEST_REJECTED -> "Der sichere Agent-Service hat den Auftrag abgelehnt."
        AgentDraftPrIssue.REQUEST_NOT_FOUND -> "Der Draft-PR-Auftrag wurde nicht gefunden."
        AgentDraftPrIssue.REQUEST_FAILED,
        AgentDraftPrIssue.SERVICE_UNAVAILABLE ->
            "Der Draft-PR-Auftrag konnte nicht sicher übermittelt werden."
    }

    private fun emptyDraftPrState(): AgentDraftPrUiState {
        approvedPlanBinding = null
        return AgentDraftPrUiState(serverAvailable = agentDraftPrGateway.serverAvailable)
    }

    private fun planBinding(plan: AgentImplementationPlan): String {
        return listOf(plan.planId, plan.baseCommitSha, plan.branchName).joinToString("|")
    }

    private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

    override fun onCleared() {
        draftPrJob?.cancel()
        draftPrCancellationJob?.cancel()
        super.onCleared()
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
