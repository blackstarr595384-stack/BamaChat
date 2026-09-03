package com.example.bamachat.ui.screen

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import com.example.bamachat.shared.core.github.AgentImplementationPlan
import com.example.bamachat.shared.core.github.AgentDraftPrStatus
import com.example.bamachat.shared.core.github.AgentValidationId
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
import com.example.bamachat.ui.viewmodel.AgentDraftPrUiPhase
import com.example.bamachat.ui.viewmodel.AgentDraftPrUiState
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
        composeRule.onAllNodesWithText("README.md: Die Grenze ist belegt.", substring = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Evidenz anzeigen"))
        composeRule.onNodeWithText("Evidenz anzeigen").performClick()
        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("README.md: Die Grenze ist belegt.", substring = true))
        composeRule.onNodeWithText("README.md: Die Grenze ist belegt.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Testplan anzeigen"))
        composeRule.onNodeWithText("Testplan anzeigen").performClick()
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

        listOf(
            "Commit erstellen",
            "Auto-Merge",
            "Approve",
            "Force-Push",
            "GitHub-Token",
            "Installation Token",
            "Beliebigen Branch eingeben",
            "Shell-Befehl"
        ).forEach { text ->
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

    @Test
    fun planButtonExistsOnlyForValidatedProposalAndDoesNotStartAutomatically() {
        var preparedProposalId: String? = null
        var submitCalls = 0
        val invalidProposal = successState().proposals.single().copy(
            id = proposalId(2),
            title = "Workflow verändern",
            affectedPaths = listOf(".github/workflows/release.yml")
        )
        val invalidIdentifierProposal = successState().proposals.single().copy(
            id = "safe-boundary",
            title = "Ungültige kurze Vorschlags-ID"
        )
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = successState().copy(
                        proposals = successState().proposals +
                            invalidProposal + invalidIdentifierProposal
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {},
                    onPreparePlan = { preparedProposalId = it },
                    onSubmitDraftPr = { submitCalls++ }
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list").performScrollToNode(
            hasText("Umsetzungsplan vorbereiten")
        )
        composeRule.onNodeWithTag("github_intelligence_prepare_plan_$PROPOSAL_ID")
            .assertIsDisplayed()
            .performClick()
        composeRule.onAllNodesWithTag("github_intelligence_prepare_plan_${invalidProposal.id}")
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("github_intelligence_prepare_plan_safe-boundary")
            .assertCountEquals(0)
        assertEquals(PROPOSAL_ID, preparedProposalId)
        assertEquals(0, submitCalls)
    }

    @Test
    fun centralEligibilityHidesPlanButtonsForEveryInvalidBoundary() {
        val paths = (1..13).map { "docs/evidence-$it.md" }
        val base = successState().proposals.single()
        val invalid = listOf(
            base.copy(
                id = proposalId(3),
                title = "Zu viele Belege",
                evidence = paths.map { GitHubProposalEvidence(it, "Beleg") },
                affectedPaths = listOf(paths.first())
            ) to "Zu viele Belege",
            base.copy(
                id = proposalId(4),
                title = "Zu viele betroffene Dateien",
                evidence = paths.map { GitHubProposalEvidence(it, "Beleg") },
                affectedPaths = paths
            ) to "Zu viele betroffene Dateien",
            base.copy(
                id = proposalId(5),
                title = "Eingebetteter Git-Befehl",
                suggestedChanges = listOf("Danach git push origin main ausführen")
            ) to "Eingebetteter Git-Befehl",
            base.copy(
                id = proposalId(6),
                title = "Eingebetteter Gradle-Befehl",
                suggestedChanges = listOf("Bitte .\\gradlew.bat :app:testDebugUnitTest starten")
            ) to "Eingebetteter Gradle-Befehl",
            base.copy(
                id = proposalId(7),
                title = "Eingebetteter ADB-Befehl",
                suggestedChanges = listOf("Dokumentation anpassen und danach adb shell starten")
            ) to "Eingebetteter ADB-Befehl",
            base.copy(
                id = proposalId(8),
                title = "Zu viele Schritte",
                suggestedChanges = (1..13).map { "Deklarativen Schritt $it ergänzen" }
            ) to "Zu viele Schritte",
            base.copy(
                id = proposalId(9),
                title = "",
                summary = "Vorschlag mit leerem Titel"
            ) to "Vorschlag mit leerem Titel",
            base.copy(
                id = proposalId(10),
                title = "x".repeat(1_001),
                summary = "Vorschlag mit überlangem Titel"
            ) to "Vorschlag mit überlangem Titel",
            base.copy(
                id = proposalId(11),
                title = "Vorschlag mit leerer Zusammenfassung",
                summary = ""
            ) to "Vorschlag mit leerer Zusammenfassung",
            base.copy(
                id = proposalId(12),
                title = "Vorschlag mit überlanger Zusammenfassung",
                summary = "x".repeat(1_001)
            ) to "Vorschlag mit überlanger Zusammenfassung",
            base.copy(
                id = "safe-boundary",
                title = "Ungültige Vorschlags-ID"
            ) to "Ungültige Vorschlags-ID"
        )
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = successState().copy(
                        snapshotSummary = successState().snapshotSummary?.copy(
                            selectedFileCount = paths.size + 1,
                            selectedPaths = listOf("README.md") + paths
                        ),
                        proposals = invalid.map { it.first }
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        val list = composeRule.onNodeWithTag("github_intelligence_list")
        invalid.forEach { (proposal, locator) ->
            list.performScrollToNode(hasText(locator))
            composeRule.onNodeWithText(locator).assertExists()
            composeRule.onAllNodesWithTag("github_intelligence_prepare_plan_${proposal.id}")
                .assertCountEquals(0)
        }
    }

    @Test
    fun proposalDetailsStartCollapsedAndEachSectionRoundTrips() {
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

        val sections = listOf(
            "Evidenz" to "• README.md: Die Grenze ist belegt.",
            "Betroffene Dateien" to "• README.md",
            "Empfohlene Änderung" to "• Dokumentation präzisieren",
            "Testplan" to "• Policy-Test ergänzen",
            "Einschränkungen" to "• Keine Laufzeittests ausgeführt"
        )
        sections.forEach { (_, content) ->
            composeRule.onAllNodesWithText(content).assertCountEquals(0)
        }
        val list = composeRule.onNodeWithTag("github_intelligence_list")
        sections.forEach { (title, content) ->
            list.performScrollToNode(hasText("$title anzeigen"))
            composeRule.onNodeWithText("$title anzeigen").performClick()
            list.performScrollToNode(hasText(content))
            composeRule.onNodeWithText(content).assertIsDisplayed()
            list.performScrollToNode(hasText("$title ausblenden"))
            composeRule.onNodeWithText("$title ausblenden").performClick()
            composeRule.onAllNodesWithText(content).assertCountEquals(0)
        }
    }

    @Test
    fun longAllowedPathRemainsFullyReadableAfterExpansion() {
        val longPath = "app/src/main/java/com/example/bamachat/" +
            "feature/very-long-but-safe-directory-name/".repeat(4) +
            "LongAllowedRepositoryPolicyScreen.kt"
        val proposal = successState().proposals.single().copy(
            affectedPaths = listOf(longPath),
            evidence = listOf(GitHubProposalEvidence(longPath, "Vollständiger Pfadbeleg"))
        )
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = successState().copy(
                        snapshotSummary = successState().snapshotSummary?.copy(
                            selectedPaths = listOf(longPath)
                        ),
                        proposals = listOf(proposal)
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        val list = composeRule.onNodeWithTag("github_intelligence_list")
        list.performScrollToNode(hasText("Betroffene Dateien anzeigen"))
        composeRule.onNodeWithText("Betroffene Dateien anzeigen").performClick()
        list.performScrollToNode(hasText(longPath, substring = true))
        composeRule.onNodeWithText("• $longPath")
            .assertTextEquals("• $longPath")
            .assertIsDisplayed()
    }

    @Test
    fun affectedPathWithoutMatchingEvidenceHasNoPlanButton() {
        val unsupported = successState().proposals.single().copy(
            id = proposalId(13),
            title = "Unbelegte Änderung",
            affectedPaths = listOf("app/src/main/java/example.kt")
        )
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = successState().copy(
                        snapshotSummary = successState().snapshotSummary?.copy(
                            selectedPaths = listOf("README.md", "app/src/main/java/example.kt")
                        ),
                        proposals = listOf(unsupported)
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Unbelegte Änderung"))
        composeRule.onNodeWithText("Unbelegte Änderung").assertIsDisplayed()
        composeRule.onAllNodesWithTag("github_intelligence_prepare_plan_${unsupported.id}")
            .assertCountEquals(0)
    }

    @Test
    fun confirmationShowsBoundPlanAndRequiresExplicitApproval() {
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(serverAvailable = true, approved = false),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        val list = composeRule.onNodeWithTag("github_intelligence_list")
        list.performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithText("Geprüfter Umsetzungsplan").assertIsDisplayed()
        list.performScrollToNode(hasText("Basis-Commit: $SHA"))
        composeRule.onNodeWithText("Basis-Commit: $SHA").assertIsDisplayed()
        list.performScrollToNode(hasText("Agenten-Branch: bamachat-agent/12345678-sichere-grenze"))
        composeRule.onNodeWithText("Agenten-Branch: bamachat-agent/12345678-sichere-grenze")
            .assertIsDisplayed()
        list.performScrollToNode(hasText("README.md", substring = true))
        composeRule.onNodeWithTag("github_intelligence_draft_affected_path_0")
            .assertTextEquals("• README.md")
            .assertIsDisplayed()
        list.performScrollToNode(hasText("Draft-PR-Auftrag freigeben"))
        composeRule.onNodeWithTag("github_intelligence_draft_submit")
            .assertTextEquals("Draft-PR-Auftrag freigeben")
            .assertIsNotEnabled()
    }

    @Test
    fun disabledServerStateIsNeutralAndCannotSubmit() {
        var submitCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(serverAvailable = false, approved = true),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {},
                    onSubmitDraftPr = { submitCalls++ }
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithTag("github_intelligence_server_unavailable")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("github_intelligence_draft_submit").assertIsNotEnabled()
        composeRule.onAllNodesWithTag("github_intelligence_error").assertCountEquals(0)
        assertEquals(0, submitCalls)
    }

    @Test
    fun approvedDoubleClickProducesOneSubmissionCallback() {
        var submitCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(serverAvailable = true, approved = true),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {},
                    onSubmitDraftPr = { submitCalls++ }
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithTag("github_intelligence_draft_submit")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals(1, submitCalls)
    }

    @Test
    fun serverAcceptedDisablesSubmitAndApprovalWithoutShowingLink() {
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(
                        serverAvailable = true,
                        approved = true,
                        draftPhase = AgentDraftPrUiPhase.SERVER_ACCEPTED,
                        serverStatus = AgentDraftPrStatus.SERVER_ACCEPTED
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithTag("github_intelligence_draft_submit")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("github_intelligence_draft_approval")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onAllNodesWithTag("github_intelligence_draft_pr_link")
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("github_intelligence_draft_cancel")
            .assertCountEquals(0)
    }

    @Test
    fun draftPrCreatedDisablesResubmissionAndShowsOnlyValidatedLink() {
        var openedUrl: String? = null
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(
                        serverAvailable = true,
                        approved = true,
                        draftPhase = AgentDraftPrUiPhase.DRAFT_PR_CREATED,
                        serverStatus = AgentDraftPrStatus.DRAFT_PR_CREATED,
                        draftPullRequestNumber = 42L,
                        draftPullRequestUrl = DRAFT_PR_URL
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {},
                    onOpenDraftPullRequest = { openedUrl = it }
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithTag("github_intelligence_draft_submit")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("github_intelligence_draft_approval")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("github_intelligence_draft_pr_link")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onAllNodesWithTag("github_intelligence_draft_cancel")
            .assertCountEquals(0)

        assertEquals(DRAFT_PR_URL, openedUrl)
    }

    @Test
    fun invalidDraftPrUrlIsNeverDisplayed() {
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(
                        serverAvailable = true,
                        approved = true,
                        draftPhase = AgentDraftPrUiPhase.DRAFT_PR_CREATED,
                        serverStatus = AgentDraftPrStatus.DRAFT_PR_CREATED,
                        draftPullRequestNumber = 42L,
                        draftPullRequestUrl =
                            "https://example.com/blackstarr595384-stack/BamaChat/pull/42"
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onAllNodesWithTag("github_intelligence_draft_pr_link")
            .assertCountEquals(0)
    }

    @Test
    fun cancellingShowsNeutralProgressAndDisablesAllDraftActions() {
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(
                        serverAvailable = true,
                        approved = false,
                        draftPhase = AgentDraftPrUiPhase.CANCELLING
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithTag("github_intelligence_draft_cancelling")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("github_intelligence_draft_cancel")
            .assertCountEquals(1)
        composeRule.onNodeWithText("Draft-PR-Auftrag wird sicher abgebrochen …")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("github_intelligence_draft_submit")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("github_intelligence_draft_approval")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("github_intelligence_draft_cancel")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onAllNodesWithText("Auftrag abgebrochen").assertCountEquals(0)
    }

    @Test
    fun cancelledIsVisibleOnlyAfterConfirmedStatus() {
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(
                        serverAvailable = true,
                        approved = false,
                        draftPhase = AgentDraftPrUiPhase.CANCELLED,
                        serverStatus = AgentDraftPrStatus.CANCELLED
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithText("Auftrag abgebrochen")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("github_intelligence_draft_cancelling")
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("github_intelligence_draft_cancel")
            .assertCountEquals(0)
    }

    @Test
    fun cancellationErrorShowsOnlySafeLocalMessage() {
        val safeMessage = "Der Draft-PR-Auftrag konnte nicht sicher abgebrochen werden."
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(
                        serverAvailable = true,
                        approved = false,
                        draftPhase = AgentDraftPrUiPhase.ERROR
                    ).copy(
                        draftPr = draftPlanState(
                            serverAvailable = true,
                            approved = false,
                            draftPhase = AgentDraftPrUiPhase.ERROR
                        ).draftPr.copy(safeMessage = safeMessage)
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithTag("github_intelligence_draft_message")
            .performScrollTo()
            .assertTextEquals(safeMessage)
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("raw cancellation", substring = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("github_intelligence_draft_cancel")
            .assertCountEquals(0)
    }

    @Test
    fun submittingCancellationCallbackRunsExactlyOnce() {
        var cancelCalls = 0
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(
                        serverAvailable = true,
                        approved = false,
                        draftPhase = AgentDraftPrUiPhase.SUBMITTING
                    ),
                    onBack = {},
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {},
                    onCancelDraftPr = { cancelCalls++ }
                )
            }
        }

        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Geprüfter Umsetzungsplan"))
        composeRule.onNodeWithTag("github_intelligence_draft_cancel")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, cancelCalls)
    }

    @Test
    fun savedStateRestorationKeepsSelectionListPositionAndDoesNotSubmit() {
        var submitCalls = 0
        var backCalls = 0
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = draftPlanState(serverAvailable = true, approved = false),
                    onBack = { backCalls++ },
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {},
                    onSubmitDraftPr = { submitCalls++ }
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, submitCalls)
        composeRule.onNodeWithTag("github_intelligence_list").performScrollToNode(
            hasText("Private Repositories und GitHub-Schreibzugriffe", substring = true)
        )
        composeRule.onNodeWithText(
            "Private Repositories und GitHub-Schreibzugriffe",
            substring = true
        ).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(0, submitCalls)
        composeRule.onNodeWithText(
            "Private Repositories und GitHub-Schreibzugriffe",
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("github_intelligence_list")
            .performScrollToNode(hasText("Analyse konfigurieren"))
        composeRule.onNodeWithTag("github_intelligence_area").assertTextEquals("Sicherheit")
        composeRule.onNodeWithTag("github_intelligence_ref")
            .assertTextEquals("phase-7.5b-stable")
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(1, backCalls)
        assertEquals(0, submitCalls)
    }

    @Test
    fun submittingAndCancellingDisableControlsAndConsumeSystemBack() {
        var state by mutableStateOf(
            draftPlanState(
                serverAvailable = true,
                approved = true,
                draftPhase = AgentDraftPrUiPhase.SUBMITTING
            )
        )
        var headerBackCalls = 0
        var parentBackCalls = 0
        val parentCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentBackCalls++
            }
        }
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.addCallback(
                composeRule.activity,
                parentCallback
            )
        }
        composeRule.setContent {
            BamaChatTheme {
                GitHubIntelligenceContent(
                    state = state,
                    onBack = { headerBackCalls++ },
                    onSelectArea = {},
                    onSelectRef = {},
                    onStart = {},
                    onCancel = {}
                )
            }
        }

        fun assertOperationBlocked() {
            composeRule.onNodeWithContentDescription("Zurück").assertIsNotEnabled()
            composeRule.onNodeWithTag("github_intelligence_area").assertIsNotEnabled()
            composeRule.onNodeWithTag("github_intelligence_ref").assertIsNotEnabled()
            composeRule.onNodeWithTag("github_intelligence_start").assertIsNotEnabled()
            composeRule.runOnIdle {
                composeRule.activity.onBackPressedDispatcher.onBackPressed()
            }
            assertEquals(0, headerBackCalls)
            assertEquals(0, parentBackCalls)
        }

        assertOperationBlocked()
        composeRule.runOnIdle {
            state = state.copy(
                draftPr = state.draftPr.copy(phase = AgentDraftPrUiPhase.CANCELLING)
            )
        }
        assertOperationBlocked()
        composeRule.runOnIdle {
            state = state.copy(
                draftPr = state.draftPr.copy(
                    phase = AgentDraftPrUiPhase.CANCELLED,
                    serverStatus = AgentDraftPrStatus.CANCELLED
                )
            )
        }
        composeRule.onNodeWithContentDescription("Zurück")
            .assertIsEnabled()
            .performClick()
        assertEquals(1, headerBackCalls)
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        assertEquals(1, parentBackCalls)
        composeRule.runOnIdle {
            parentCallback.remove()
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
                    id = PROPOSAL_ID,
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

    private fun draftPlanState(
        serverAvailable: Boolean,
        approved: Boolean,
        draftPhase: AgentDraftPrUiPhase = AgentDraftPrUiPhase.PLAN_READY,
        serverStatus: AgentDraftPrStatus? = null,
        draftPullRequestNumber: Long? = null,
        draftPullRequestUrl: String? = null
    ): GitHubIntelligenceUiState {
        return successState().copy(
            draftPr = AgentDraftPrUiState(
                serverAvailable = serverAvailable,
                phase = draftPhase,
                activeProposalId = PROPOSAL_ID,
                plan = AgentImplementationPlan(
                    planId = "plan-1234567890abcdef1234",
                    proposalId = PROPOSAL_ID,
                    title = "Sichere Grenze dokumentieren",
                    summary = "Die Sicherheitsgrenze bleibt prüfbar.",
                    repository = com.example.bamachat.shared.core.github.GitHubRepositoryPolicy.repository,
                    baseRef = "phase-7.5b-stable",
                    baseCommitSha = SHA,
                    branchName = "bamachat-agent/12345678-sichere-grenze",
                    evidencePaths = listOf("README.md"),
                    affectedPaths = listOf("README.md"),
                    changeSteps = listOf("Dokumentation präzisieren"),
                    validationSteps = listOf(AgentValidationId.DIFF_CHECK),
                    risk = GitHubProposalRisk.LOW,
                    limitations = listOf("Kein Live-Auftrag"),
                    createdAt = 1_800_000_000L,
                    expiresAt = 1_800_001_800L
                ),
                explicitApprovalChecked = approved,
                serverStatus = serverStatus,
                draftPullRequestNumber = draftPullRequestNumber,
                draftPullRequestUrl = draftPullRequestUrl
            )
        )
    }

    companion object {
        private const val SHA = "9a5c5e58711ad470374e4ab134b61ce8bc8399b8"
        private const val PROPOSAL_ID =
            "proposal-0000000000000000000000000000000000000000000000000000000000000001"
        private const val DRAFT_PR_URL =
            "https://github.com/blackstarr595384-stack/BamaChat/pull/42"
    }

    private fun proposalId(index: Int): String =
        "proposal-${index.toString(16).padStart(64, '0')}"
}
