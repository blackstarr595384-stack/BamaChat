package com.example.bamachat.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.example.bamachat.shared.core.github.GitHubAnalysisArea
import com.example.bamachat.shared.core.github.GitHubImprovementProposal
import com.example.bamachat.shared.core.github.GitHubProposalBenefit
import com.example.bamachat.shared.core.github.GitHubProposalCategory
import com.example.bamachat.shared.core.github.GitHubProposalConfidence
import com.example.bamachat.shared.core.github.GitHubProposalEffort
import com.example.bamachat.shared.core.github.GitHubProposalEvidence
import com.example.bamachat.shared.core.github.GitHubProposalRisk
import com.example.bamachat.ui.theme.BamaChatTheme
import com.example.bamachat.ui.viewmodel.GitHubIntelligencePhase
import com.example.bamachat.ui.viewmodel.GitHubIntelligenceUiState
import com.example.bamachat.ui.viewmodel.GitHubSnapshotSummaryUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GitHubIntelligenceScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readOnlySafetyRepositoryAndProposalDataAreVisible() {
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = successState(),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("GitHub Intelligence").assertIsDisplayed()
        composeRule.onNodeWithText("Nur lesen").assertIsDisplayed()
        composeRule.onNodeWithText("blackstarr595384-stack/BamaChat").assertIsDisplayed()
        composeRule.onNodeWithText(
            "BamaChat liest freigegebene Repositorydaten und erstellt Vorschläge.",
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Sichere Grenze dokumentieren"))
        composeRule.onNodeWithText("Sichere Grenze dokumentieren").assertIsDisplayed()
        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("README.md: Die Grenze ist belegt.", substring = true))
        composeRule.onNodeWithText("README.md: Die Grenze ist belegt.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Policy-Test ergänzen", substring = true))
        composeRule.onNodeWithText("Policy-Test ergänzen", substring = true).assertIsDisplayed()
    }

    @Test
    fun compositionDoesNotStartAnalysisAutomatically() {
        var startCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = GitHubIntelligenceUiState(),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = { startCalls++ },
                    onCancel = {}
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, startCalls)
        composeRule.onNodeWithTag("github_intelligence_start").assertIsNotEnabled()
    }

    @Test
    fun writeActionsAndSensitiveTechnicalContentAreAbsent() {
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = successState(),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        listOf("Commit", "Push", "Pull Request", "Merge", "Anwenden", "Workflow starten").forEach { text ->
            composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
        listOf("Authorization", "Bearer", "api.github.com", "https://").forEach { text ->
            composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun loadingStateShowsCancelAndPreventsSecondStart() {
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = GitHubIntelligenceUiState(
                        selectedArea = GitHubAnalysisArea.SECURITY,
                        phase = GitHubIntelligencePhase.ANALYZING
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("github_intelligence_start").assertCountEquals(0)
        composeRule.onNodeWithTag("github_intelligence_cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Strukturierte Vorschläge werden erstellt …").assertIsDisplayed()
    }

    @Test
    fun loadingCancelButtonIsEnabledReadableAndUsesResponsiveWidth() {
        var startCalls = 0
        var cancelCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = GitHubIntelligenceUiState(
                        selectedArea = GitHubAnalysisArea.SECURITY,
                        phase = GitHubIntelligencePhase.LOADING_REPOSITORY
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = { startCalls++ },
                    onCancel = { cancelCalls++ }
                )
            }
        }

        composeRule.onAllNodesWithTag("github_intelligence_start").assertCountEquals(0)
        composeRule.onNodeWithTag("github_intelligence_cancel")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextEquals("Analyse abbrechen")
            .assertWidthIsAtLeast(200.dp)
            .performClick()
        assertEquals(0, startCalls)
        assertEquals(1, cancelCalls)
    }

    @Test
    fun noActionableProposalsShowsNeutralStateAndAllowsAnotherAnalysis() {
        var startCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = noResultsState(),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = { startCalls++ },
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_start")
            .assertIsEnabled()
            .performClick()
        assertEquals(1, startCalls)
        composeRule.onNodeWithTag("github_intelligence_list").performScrollToNode(
            hasText(
                "Für diesen Analysebereich wurden keine ausreichend belegten Vorschläge gefunden."
            )
        )
        composeRule.onNodeWithTag("github_intelligence_no_results").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Für diesen Analysebereich wurden keine ausreichend belegten Vorschläge gefunden."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Snapshot-Zusammenfassung"))
        composeRule.onNodeWithText("Snapshot-Zusammenfassung").assertIsDisplayed()
        composeRule.onAllNodesWithTag("github_intelligence_error").assertCountEquals(0)
        composeRule.onAllNodesWithTag("github_intelligence_proposal").assertCountEquals(0)
        listOf(
            "INVALID_JSON",
            "AMBIGUOUS_JSON_PAYLOAD",
            "BEGIN UNTRUSTED MODEL OUTPUT",
            "raw model response"
        ).forEach { text ->
            composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
    }

    private fun successState(): GitHubIntelligenceUiState {
        return GitHubIntelligenceUiState(
            selectedArea = GitHubAnalysisArea.SECURITY,
            phase = GitHubIntelligencePhase.SUCCESS,
            snapshotSummary = GitHubSnapshotSummaryUi(
                resolvedRef = "phase-7.5b-stable",
                headCommitSha = "919b25230ab418817460ec6e0831dc69b6e60d08",
                treeEntryCount = 120,
                selectedFileCount = 3,
                selectedPaths = listOf("README.md"),
                truncated = false
            ),
            proposals = listOf(
                GitHubImprovementProposal(
                    id = "safe-boundary",
                    title = "Sichere Grenze dokumentieren",
                    summary = "Die Sicherheitsgrenze bleibt prüfbar.",
                    category = GitHubProposalCategory.SECURITY,
                    benefit = GitHubProposalBenefit.HIGH,
                    risk = GitHubProposalRisk.LOW,
                    effort = GitHubProposalEffort.SMALL,
                    confidence = GitHubProposalConfidence.HIGH,
                    evidence = listOf(
                        GitHubProposalEvidence("README.md", "Die Grenze ist belegt.")
                    ),
                    affectedPaths = listOf("README.md"),
                    suggestedChanges = listOf("Dokumentation präzisieren"),
                    testPlan = listOf("Policy-Test ergänzen"),
                    limitations = listOf("Keine Laufzeittests ausgeführt")
                )
            )
        )
    }

    private fun noResultsState(): GitHubIntelligenceUiState {
        return successState().copy(
            phase = GitHubIntelligencePhase.NO_RESULTS,
            proposals = emptyList(),
            errorMessage = null
        )
    }
}
