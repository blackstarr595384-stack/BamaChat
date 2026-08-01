package com.example.bamachat.ui.viewmodel

import com.example.bamachat.service.AndroidGitHubProposalAnalyzer
import com.example.bamachat.service.GitHubProposalAnalysisIssue
import com.example.bamachat.service.GitHubProposalAnalysisResult
import com.example.bamachat.service.GitHubProposalAnalyzer
import com.example.bamachat.shared.core.github.GitHubAnalysisArea
import com.example.bamachat.shared.core.github.GitHubImprovementProposal
import com.example.bamachat.shared.core.github.GitHubProposalBenefit
import com.example.bamachat.shared.core.github.GitHubProposalCategory
import com.example.bamachat.shared.core.github.GitHubProposalConfidence
import com.example.bamachat.shared.core.github.GitHubProposalEffort
import com.example.bamachat.shared.core.github.GitHubProposalEvidence
import com.example.bamachat.shared.core.github.GitHubProposalRisk
import com.example.bamachat.shared.core.github.GitHubReadIssue
import com.example.bamachat.shared.core.github.GitHubReadOnlyRepositoryGateway
import com.example.bamachat.shared.core.github.GitHubReadResult
import com.example.bamachat.shared.core.github.GitHubRepositoryContext
import com.example.bamachat.shared.core.github.GitHubRepositoryMetadata
import com.example.bamachat.shared.core.github.GitHubRepositoryPolicy
import com.example.bamachat.shared.core.github.GitHubRepositoryRef
import com.example.bamachat.shared.core.github.GitHubRepositorySnapshot
import com.example.bamachat.shared.core.github.GitHubResolvedRef
import com.example.bamachat.shared.core.github.GitHubTextFile
import com.example.bamachat.shared.core.github.GitHubTreeEntry
import com.example.bamachat.shared.core.github.GitHubTreeEntryType
import com.example.bamachat.shared.core.github.GitHubTruncationInformation
import com.example.bamachat.shared.core.github.RepositoryContextBuilder
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubIntelligenceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun constructionDoesNotStartNetworkOrAi() {
        val gateway = FakeGateway()
        val analyzer = FakeAnalyzer()

        val viewModel = viewModel(gateway, analyzer)

        assertEquals(GitHubIntelligencePhase.IDLE, viewModel.uiState.value.phase)
        assertEquals(0, gateway.snapshotCalls)
        assertEquals(0, analyzer.calls)
    }

    @Test
    fun explicitStartBuildsSnapshotAndReturnsSuccess() = runTest {
        val gateway = FakeGateway()
        val analyzer = FakeAnalyzer()
        val viewModel = viewModel(gateway, analyzer)
        viewModel.selectArea(GitHubAnalysisArea.ARCHITECTURE)

        viewModel.startAnalysis()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GitHubIntelligencePhase.SUCCESS, state.phase)
        assertEquals(1, gateway.snapshotCalls)
        assertEquals(1, analyzer.calls)
        assertEquals(listOf("README.md"), state.snapshotSummary?.selectedPaths)
        assertEquals(listOf(proposal()), state.proposals)
        assertNull(state.errorMessage)
        assertFalse(state.analysisInProgress)
    }

    @Test
    fun doubleClickStartsExactlyOneAnalysis() = runTest {
        val release = CompletableDeferred<Unit>()
        val gateway = FakeGateway()
        val analyzer = FakeAnalyzer { _, _ ->
            release.await()
            GitHubProposalAnalysisResult.Success(listOf(proposal()))
        }
        val viewModel = viewModel(gateway, analyzer)
        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        viewModel.startAnalysis()
        runCurrent()
        viewModel.startAnalysis()
        runCurrent()

        assertEquals(1, gateway.snapshotCalls)
        assertEquals(1, analyzer.calls)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(GitHubIntelligencePhase.SUCCESS, viewModel.uiState.value.phase)
    }

    @Test
    fun cancelEndsAnalysisWithoutLeavingLoadingState() = runTest {
        val gateway = FakeGateway()
        val analyzer = FakeAnalyzer { _, _ -> awaitCancellation() }
        val viewModel = viewModel(gateway, analyzer)
        viewModel.selectArea(GitHubAnalysisArea.TESTS)
        viewModel.startAnalysis()
        runCurrent()

        assertEquals(GitHubIntelligencePhase.ANALYZING, viewModel.uiState.value.phase)
        viewModel.cancelAnalysis()
        runCurrent()

        assertEquals(GitHubIntelligencePhase.CANCELLED, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.analysisInProgress)
        assertEquals(1, analyzer.calls)
    }

    @Test
    fun networkFailureEndsLoadingWithSafeMessage() = runTest {
        val gateway = FakeGateway(
            result = GitHubReadResult.Failure(GitHubReadIssue.RATE_LIMITED)
        )
        val analyzer = FakeAnalyzer()
        val viewModel = viewModel(gateway, analyzer)
        viewModel.selectArea(GitHubAnalysisArea.DOCUMENTATION)

        viewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(GitHubIntelligencePhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(
            "Das öffentliche GitHub-Leselimit ist erreicht. Bitte später erneut versuchen.",
            viewModel.uiState.value.errorMessage
        )
        assertEquals(0, analyzer.calls)
        assertFalse(viewModel.uiState.value.analysisInProgress)
    }

    @Test
    fun validRateLimitResetIsFormattedSafelyWithoutRawResponseData() = runTest {
        val reset = Instant.parse("2026-07-31T21:18:00Z").epochSecond
        val gateway = FakeGateway(
            result = GitHubReadResult.Failure(
                issue = GitHubReadIssue.RATE_LIMITED,
                rateLimitResetEpochSeconds = reset
            )
        )
        val viewModel = viewModel(gateway, FakeAnalyzer())
        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        viewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(
            "Das öffentliche GitHub-Leselimit ist erreicht.\n" +
                "Neuer Versuch voraussichtlich ab 23:18 Uhr.",
            GitHubRateLimitMessageFormatter.message(reset, ZoneId.of("Europe/Berlin"))
        )
        assertTrue(
            viewModel.uiState.value.errorMessage.orEmpty().contains(
                "Neuer Versuch voraussichtlich ab"
            )
        )
        assertFalse(viewModel.uiState.value.toString().contains(reset.toString()))
        assertFalse(viewModel.uiState.value.analysisInProgress)
    }

    @Test
    fun invalidRateLimitResetUsesNeutralMessage() {
        assertEquals(
            "Das öffentliche GitHub-Leselimit ist erreicht. Bitte später erneut versuchen.",
            GitHubRateLimitMessageFormatter.message(-1L, ZoneId.of("Europe/Berlin"))
        )
        assertEquals(
            "Das öffentliche GitHub-Leselimit ist erreicht. Bitte später erneut versuchen.",
            GitHubRateLimitMessageFormatter.message(Long.MAX_VALUE, ZoneId.of("Europe/Berlin"))
        )
    }

    @Test
    fun parserAndAiFailuresEndWithSafeMessages() = runTest {
        val gateway = FakeGateway()
        val parserAnalyzer = FakeAnalyzer { _, _ ->
            GitHubProposalAnalysisResult.Failure(GitHubProposalAnalysisIssue.INVALID_JSON)
        }
        val parserViewModel = viewModel(gateway, parserAnalyzer)
        parserViewModel.selectArea(GitHubAnalysisArea.SHARED_CORE)
        parserViewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(
            "Die KI-Antwort enthielt keine gültigen strukturierten Vorschläge.",
            parserViewModel.uiState.value.errorMessage
        )
        assertFalse(parserViewModel.uiState.value.analysisInProgress)

        val aiAnalyzer = FakeAnalyzer { _, _ ->
            GitHubProposalAnalysisResult.Failure(GitHubProposalAnalysisIssue.REQUEST_FAILED)
        }
        val aiViewModel = viewModel(FakeGateway(), aiAnalyzer)
        aiViewModel.selectArea(GitHubAnalysisArea.SECURITY)
        aiViewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(
            "Die KI-Analyse konnte nicht abgeschlossen werden.",
            aiViewModel.uiState.value.errorMessage
        )
        assertFalse(aiViewModel.uiState.value.analysisInProgress)
    }

    @Test
    fun everyTypedParserIssueUsesTheSameSafeUiMessage() = runTest {
        val parserIssues = listOf(
            GitHubProposalAnalysisIssue.EMPTY_RESPONSE,
            GitHubProposalAnalysisIssue.INVALID_JSON,
            GitHubProposalAnalysisIssue.MISSING_REQUIRED_FIELDS,
            GitHubProposalAnalysisIssue.UNKNOWN_EVIDENCE_PATH,
            GitHubProposalAnalysisIssue.AMBIGUOUS_JSON_PAYLOAD,
            GitHubProposalAnalysisIssue.RESPONSE_TOO_LARGE
        )

        parserIssues.forEach { issue ->
            val viewModel = viewModel(
                FakeGateway(),
                FakeAnalyzer { _, _ -> GitHubProposalAnalysisResult.Failure(issue) }
            )
            viewModel.selectArea(GitHubAnalysisArea.SECURITY)
            viewModel.startAnalysis()
            advanceUntilIdle()

            assertEquals(GitHubIntelligencePhase.ERROR, viewModel.uiState.value.phase)
            assertEquals(
                "Die KI-Antwort enthielt keine gültigen strukturierten Vorschläge.",
                viewModel.uiState.value.errorMessage
            )
            assertFalse(viewModel.uiState.value.toString().contains(issue.name))
        }
    }

    @Test
    fun noActionableProposalsIsNeutralAndKeepsSnapshotAvailable() = runTest {
        val analyzer = FakeAnalyzer { _, _ ->
            GitHubProposalAnalysisResult.NoActionableProposals
        }
        val viewModel = viewModel(FakeGateway(), analyzer)
        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        viewModel.startAnalysis()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GitHubIntelligencePhase.NO_RESULTS, state.phase)
        assertEquals(listOf("README.md"), state.snapshotSummary?.selectedPaths)
        assertTrue(state.proposals.isEmpty())
        assertNull(state.errorMessage)
        assertFalse(state.analysisInProgress)
        assertTrue(state.canStart)
        assertEquals(1, analyzer.calls)
    }

    @Test
    fun analysisCanBeStartedAgainAfterNoActionableProposals() = runTest {
        val analyzer = FakeAnalyzer { _, _ ->
            GitHubProposalAnalysisResult.NoActionableProposals
        }
        val viewModel = viewModel(FakeGateway(), analyzer)
        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        viewModel.startAnalysis()
        advanceUntilIdle()
        viewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(2, analyzer.calls)
        assertEquals(GitHubIntelligencePhase.NO_RESULTS, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.analysisInProgress)
    }

    @Test
    fun parserRepairUsesExistingSnapshotAndDoesNotReadGitHubAgain() = runTest {
        var aiCalls = 0
        val analyzer = AndroidGitHubProposalAnalyzer(
            generateReply = { _, _ ->
                aiCalls++
                ApiManager.ApiResponse(
                    success = true,
                    content = if (aiCalls == 1) "invalid model output" else validAnalyzerResponse()
                )
            }
        )
        val gateway = FakeGateway()
        val viewModel = GitHubIntelligenceViewModel(
            repositoryGateway = gateway,
            proposalAnalyzer = analyzer,
            contextBuilder = RepositoryContextBuilder()
        )
        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        viewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(1, gateway.snapshotCalls)
        assertEquals(2, aiCalls)
        assertEquals(GitHubIntelligencePhase.SUCCESS, viewModel.uiState.value.phase)
    }

    @Test
    fun rawModelResponsesNeverReachUiStateOrErrorMessage() = runTest {
        val rawResponse = "RAW_MODEL_RESPONSE_MUST_NOT_REACH_UI"
        var aiCalls = 0
        val analyzer = AndroidGitHubProposalAnalyzer(
            generateReply = { _, _ ->
                aiCalls++
                ApiManager.ApiResponse(success = true, content = rawResponse)
            }
        )
        val viewModel = GitHubIntelligenceViewModel(
            repositoryGateway = FakeGateway(),
            proposalAnalyzer = analyzer,
            contextBuilder = RepositoryContextBuilder()
        )
        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        viewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(2, aiCalls)
        assertEquals(GitHubIntelligencePhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(
            "Die KI-Antwort enthielt keine gültigen strukturierten Vorschläge.",
            viewModel.uiState.value.errorMessage
        )
        assertFalse(viewModel.uiState.value.toString().contains(rawResponse))
    }

    @Test
    fun unexpectedFailureNeverExposesRawExceptionOrLeavesLoading() = runTest {
        val analyzer = FakeAnalyzer { _, _ ->
            throw IllegalStateException("technical endpoint detail")
        }
        val viewModel = viewModel(FakeGateway(), analyzer)
        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        viewModel.startAnalysis()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GitHubIntelligencePhase.ERROR, state.phase)
        assertEquals("Die GitHub-Analyse konnte nicht abgeschlossen werden.", state.errorMessage)
        assertFalse(state.toString().contains("technical endpoint detail"))
        assertFalse(state.analysisInProgress)
    }

    @Test
    fun refreshFailurePreservesPreviousSuccessfulProposals() = runTest {
        val gateway = FakeGateway()
        val analyzer = FakeAnalyzer()
        val viewModel = viewModel(gateway, analyzer)
        viewModel.selectArea(GitHubAnalysisArea.ARCHITECTURE)
        viewModel.startAnalysis()
        advanceUntilIdle()
        gateway.result = GitHubReadResult.Failure(GitHubReadIssue.NETWORK_UNAVAILABLE)

        viewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(GitHubIntelligencePhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(listOf(proposal()), viewModel.uiState.value.proposals)
    }

    @Test
    fun changingAnalysisAreaClearsPreviousSnapshotAndProposals() = runTest {
        val viewModel = viewModel(FakeGateway(), FakeAnalyzer())
        viewModel.selectArea(GitHubAnalysisArea.ARCHITECTURE)
        viewModel.startAnalysis()
        advanceUntilIdle()

        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        val state = viewModel.uiState.value
        assertEquals(GitHubAnalysisArea.SECURITY, state.selectedArea)
        assertEquals(GitHubIntelligencePhase.IDLE, state.phase)
        assertNull(state.snapshotSummary)
        assertTrue(state.proposals.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun changingRefClearsPreviousSnapshotAndProposals() = runTest {
        val viewModel = viewModel(FakeGateway(), FakeAnalyzer())
        viewModel.selectArea(GitHubAnalysisArea.ARCHITECTURE)
        viewModel.startAnalysis()
        advanceUntilIdle()

        viewModel.selectRef(GitHubRepositoryPolicy.RELEASED_BRANCH)

        val state = viewModel.uiState.value
        assertEquals(GitHubRepositoryPolicy.RELEASED_BRANCH, state.selectedRef)
        assertEquals(GitHubIntelligencePhase.IDLE, state.phase)
        assertNull(state.snapshotSummary)
        assertTrue(state.proposals.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun selectingTheSameAreaPreservesSuccessfulState() = runTest {
        val viewModel = viewModel(FakeGateway(), FakeAnalyzer())
        viewModel.selectArea(GitHubAnalysisArea.ARCHITECTURE)
        viewModel.startAnalysis()
        advanceUntilIdle()
        val before = viewModel.uiState.value

        viewModel.selectArea(GitHubAnalysisArea.ARCHITECTURE)

        assertSame(before, viewModel.uiState.value)
    }

    @Test
    fun oldResultsNeverAppearUnderANewSelectionAfterFailure() = runTest {
        val gateway = FakeGateway()
        val viewModel = viewModel(gateway, FakeAnalyzer())
        viewModel.selectArea(GitHubAnalysisArea.ARCHITECTURE)
        viewModel.startAnalysis()
        advanceUntilIdle()

        viewModel.selectArea(GitHubAnalysisArea.SECURITY)
        gateway.result = GitHubReadResult.Failure(GitHubReadIssue.NETWORK_UNAVAILABLE)
        viewModel.startAnalysis()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GitHubAnalysisArea.SECURITY, state.selectedArea)
        assertEquals(GitHubIntelligencePhase.ERROR, state.phase)
        assertNull(state.snapshotSummary)
        assertTrue(state.proposals.isEmpty())
    }

    @Test
    fun sourceTextIsNotStoredInUiState() = runTest {
        val gateway = FakeGateway()
        val analyzer = FakeAnalyzer()
        val viewModel = viewModel(gateway, analyzer)
        viewModel.selectArea(GitHubAnalysisArea.SECURITY)

        viewModel.startAnalysis()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.toString().contains(SOURCE_TEXT))
    }

    private fun viewModel(
        gateway: FakeGateway,
        analyzer: FakeAnalyzer
    ): GitHubIntelligenceViewModel {
        return GitHubIntelligenceViewModel(
            repositoryGateway = gateway,
            proposalAnalyzer = analyzer,
            contextBuilder = RepositoryContextBuilder()
        )
    }

    private class FakeGateway(
        var result: GitHubReadResult<GitHubRepositorySnapshot> = GitHubReadResult.Success(snapshot())
    ) : GitHubReadOnlyRepositoryGateway {
        var snapshotCalls = 0

        override suspend fun readRepositoryMetadata(
            repository: GitHubRepositoryRef
        ): GitHubReadResult<GitHubRepositoryMetadata> {
            throw AssertionError("Unexpected direct metadata read")
        }

        override suspend fun resolveRef(
            repository: GitHubRepositoryRef,
            ref: String
        ): GitHubReadResult<GitHubResolvedRef> {
            throw AssertionError("Unexpected direct ref read")
        }

        override suspend fun readTree(
            repository: GitHubRepositoryRef,
            resolvedRef: GitHubResolvedRef
        ): GitHubReadResult<List<GitHubTreeEntry>> {
            throw AssertionError("Unexpected direct tree read")
        }

        override suspend fun readTextFile(
            repository: GitHubRepositoryRef,
            resolvedRef: GitHubResolvedRef,
            path: String
        ): GitHubReadResult<GitHubTextFile> {
            throw AssertionError("Unexpected direct file read")
        }

        override suspend fun readSnapshot(
            repository: GitHubRepositoryRef,
            ref: String,
            analysisArea: GitHubAnalysisArea
        ): GitHubReadResult<GitHubRepositorySnapshot> {
            snapshotCalls++
            assertEquals(GitHubRepositoryPolicy.repository, repository)
            assertTrue(GitHubRepositoryPolicy.isAllowedRef(ref))
            return result
        }
    }

    private class FakeAnalyzer(
        private val handler: suspend (
            GitHubRepositoryContext,
            GitHubAnalysisArea
        ) -> GitHubProposalAnalysisResult = { _, _ ->
            GitHubProposalAnalysisResult.Success(listOf(proposal()))
        }
    ) : GitHubProposalAnalyzer {
        var calls = 0

        override suspend fun analyze(
            context: GitHubRepositoryContext,
            analysisArea: GitHubAnalysisArea
        ): GitHubProposalAnalysisResult {
            calls++
            assertTrue(context.text.contains(RepositoryContextBuilder.BEGIN_BOUNDARY))
            return handler(context, analysisArea)
        }
    }

    companion object {
        private const val SHA = "919b25230ab418817460ec6e0831dc69b6e60d08"
        private const val SOURCE_TEXT = "SOURCE_TEXT_MUST_NOT_BE_PERSISTED"

        private fun snapshot(): GitHubRepositorySnapshot {
            val file = GitHubTextFile(
                path = "README.md",
                sha = SHA,
                text = SOURCE_TEXT,
                truncated = false,
                originalSize = SOURCE_TEXT.length.toLong()
            )
            return GitHubRepositorySnapshot(
                repository = GitHubRepositoryPolicy.repository,
                resolvedRef = GitHubRepositoryPolicy.DEFAULT_REF,
                headCommitSha = SHA,
                defaultBranch = "main",
                repositoryDescription = null,
                treeEntries = listOf(
                    GitHubTreeEntry(
                        path = file.path,
                        type = GitHubTreeEntryType.FILE,
                        size = file.originalSize,
                        sha = SHA
                    )
                ),
                selectedFiles = listOf(file),
                truncationInformation = GitHubTruncationInformation()
            )
        }

        private fun proposal(): GitHubImprovementProposal {
            return GitHubImprovementProposal(
                id = "proposal",
                title = "Grenze schärfen",
                summary = "Sicherer Vorschlag",
                category = GitHubProposalCategory.ARCHITECTURE,
                benefit = GitHubProposalBenefit.HIGH,
                risk = GitHubProposalRisk.LOW,
                effort = GitHubProposalEffort.SMALL,
                confidence = GitHubProposalConfidence.HIGH,
                evidence = listOf(GitHubProposalEvidence("README.md", "Dokumentierte Grenze")),
                affectedPaths = listOf("README.md"),
                suggestedChanges = listOf("Dokumentation präzisieren"),
                testPlan = listOf("Policy-Test ergänzen"),
                limitations = listOf("Keine Tests ausgeführt")
            )
        }

        private fun validAnalyzerResponse(): String {
            return """
                {"proposals":[{
                  "title":"Grenze schärfen",
                  "summary":"Sicherer Vorschlag",
                  "category":"SECURITY",
                  "benefit":"HIGH",
                  "risk":"LOW",
                  "effort":"SMALL",
                  "confidence":"HIGH",
                  "evidence":[{"path":"README.md","observation":"Dokumentierte Grenze"}],
                  "affectedPaths":["README.md"],
                  "suggestedChanges":["Dokumentation präzisieren"],
                  "testPlan":["Policy-Test ergänzen"],
                  "limitations":["Keine Tests ausgeführt"]
                }]}
            """.trimIndent()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
