package com.example.bamachat.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.shared.core.github.AgentDraftPrProposalEligibilityPolicy
import com.example.bamachat.shared.core.github.AgentDraftPrStatus
import com.example.bamachat.shared.core.github.AgentDraftPrUrlPolicy
import com.example.bamachat.shared.core.github.AgentImplementationPlan
import com.example.bamachat.shared.core.github.AgentValidationId
import com.example.bamachat.shared.core.github.GitHubAnalysisArea
import com.example.bamachat.shared.core.github.GitHubImprovementProposal
import com.example.bamachat.shared.core.github.GitHubProposalBenefit
import com.example.bamachat.shared.core.github.GitHubProposalCategory
import com.example.bamachat.shared.core.github.GitHubProposalConfidence
import com.example.bamachat.shared.core.github.GitHubProposalEffort
import com.example.bamachat.shared.core.github.GitHubProposalRisk
import com.example.bamachat.shared.core.github.GitHubRepositoryPolicy
import com.example.bamachat.ui.viewmodel.GitHubIntelligencePhase
import com.example.bamachat.ui.viewmodel.GitHubIntelligenceUiState
import com.example.bamachat.ui.viewmodel.GitHubIntelligenceViewModel
import com.example.bamachat.ui.viewmodel.AgentDraftPrUiPhase

@Composable
fun GitHubIntelligenceScreen(
    onBack: () -> Unit,
    viewModel: GitHubIntelligenceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    GitHubIntelligenceContent(
        state = state,
        onBack = onBack,
        onSelectArea = viewModel::selectArea,
        onSelectRef = viewModel::selectRef,
        onStart = viewModel::startAnalysis,
        onCancel = viewModel::cancelAnalysis,
        onPreparePlan = viewModel::prepareImplementationPlan,
        onToggleDraftApproval = viewModel::setDraftPrApproval,
        onSubmitDraftPr = viewModel::submitDraftPrRequest,
        onCancelDraftPr = viewModel::cancelDraftPrRequest,
        onOpenDraftPullRequest = { url ->
            if (AgentDraftPrUrlPolicy.isAllowed(
                    url,
                    state.draftPr.draftPullRequestNumber
                )
            ) {
                uriHandler.openUri(url)
            }
        }
    )
}

@Composable
internal fun GitHubIntelligenceContent(
    state: GitHubIntelligenceUiState,
    onBack: () -> Unit,
    onSelectArea: (GitHubAnalysisArea) -> Unit,
    onSelectRef: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onPreparePlan: (String) -> Unit = {},
    onToggleDraftApproval: (Boolean) -> Unit = {},
    onSubmitDraftPr: () -> Unit = {},
    onCancelDraftPr: () -> Unit = {},
    onOpenDraftPullRequest: (String) -> Unit = {}
) {
    BackHandler(enabled = state.draftPr.operationInProgress) {}
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071A2A), Color(0xFF102A43), Color(0xFF07111F))
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("github_intelligence_list")
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Header(
                    onBack = onBack,
                    enabled = !state.draftPr.operationInProgress
                )
            }
            item {
                ReadOnlyNotice(state)
            }
            item {
                AnalysisControls(
                    state = state,
                    onSelectArea = onSelectArea,
                    onSelectRef = onSelectRef,
                    onStart = onStart,
                    onCancel = onCancel
                )
            }
            state.snapshotSummary?.let { summary ->
                item {
                    InfoCard(title = "Snapshot-Zusammenfassung") {
                        Text(
                            text = "Ref: ${summary.resolvedRef}",
                            color = Color.White
                        )
                        Text(
                            text = "${summary.selectedFileCount} freigegebene Dateien aus " +
                                "${summary.treeEntryCount} sicheren Baumeinträgen",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (summary.truncated) {
                            Text(
                                text = "Der Snapshot wurde an sicheren Größenlimits gekürzt.",
                                color = Color(0xFFFFD28A),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            state.errorMessage?.let { error ->
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_intelligence_error"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF7B2635)
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White
                        )
                    }
                }
            }
            if (state.phase == GitHubIntelligencePhase.NO_RESULTS) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_intelligence_no_results"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF23445F)
                    ) {
                        Text(
                            text = "Für diesen Analysebereich wurden keine ausreichend belegten " +
                                "Vorschläge gefunden.",
                            modifier = Modifier.padding(12.dp),
                            color = Color.White
                        )
                    }
                }
            }
            if (state.proposals.isNotEmpty()) {
                item {
                    Text(
                        text = "Priorisierte Vorschläge",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                val allowedPlanPaths = state.snapshotSummary?.selectedPaths?.toSet().orEmpty()
                items(state.proposals, key = { it.id }) { proposal ->
                    ProposalCard(
                        proposal = proposal,
                        canCreatePlan = !state.draftPr.operationInProgress &&
                            AgentDraftPrProposalEligibilityPolicy.validate(
                                proposal,
                                allowedPlanPaths
                            ) == null,
                        onPreparePlan = { onPreparePlan(proposal.id) }
                    )
                }
            }
            state.draftPr.plan?.let { plan ->
                item(key = "draft-pr-plan-${plan.planId}") {
                    AgentDraftPrPlanCard(
                        state = state,
                        plan = plan,
                        onToggleApproval = onToggleDraftApproval,
                        onSubmit = onSubmitDraftPr,
                        onCancel = onCancelDraftPr,
                        onOpenDraftPullRequest = onOpenDraftPullRequest
                    )
                }
            }
            state.draftPr.safeMessage?.let { message ->
                if (state.draftPr.plan == null) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_intelligence_draft_message"),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF23445F)
                        ) {
                            Text(message, modifier = Modifier.padding(12.dp), color = Color.White)
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Private Repositories und GitHub-Schreibzugriffe werden in dieser Phase nicht unterstützt.",
                    modifier = Modifier.padding(bottom = 20.dp),
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = Color.White
            )
        }
        Column {
            Text(
                text = "GitHub Intelligence",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Strukturierte Repository-Verbesserungsvorschläge",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ReadOnlyNotice(state: GitHubIntelligenceUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("github_intelligence_read_only"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF174D42)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.GppGood, contentDescription = null, tint = Color(0xFF9AF2CE))
                Text(
                    text = "Nur lesen",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${state.repositoryOwner}/${state.repositoryName}",
                modifier = Modifier.testTag("github_intelligence_repository"),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "BamaFlow liest freigegebene Repositorydaten und erstellt Vorschläge. " +
                    "Es verändert keinen Code und schreibt nichts auf GitHub.",
                modifier = Modifier.testTag("github_intelligence_safety_description"),
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AnalysisControls(
    state: GitHubIntelligenceUiState,
    onSelectArea: (GitHubAnalysisArea) -> Unit,
    onSelectRef: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    InfoCard(title = "Analyse konfigurieren") {
        SelectionMenu(
            label = "Analysebereich",
            value = state.selectedArea?.label() ?: "Bitte auswählen",
            options = GitHubAnalysisArea.entries.map { it.label() to { onSelectArea(it) } },
            enabled = !state.analysisInProgress && !state.draftPr.operationInProgress,
            testTag = "github_intelligence_area"
        )
        SelectionMenu(
            label = "Freigegebener Ref",
            value = state.selectedRef,
            options = GitHubRepositoryPolicy.allowedRefs.sorted().map { ref -> ref to { onSelectRef(ref) } },
            enabled = !state.analysisInProgress && !state.draftPr.operationInProgress,
            testTag = "github_intelligence_ref"
        )
        if (state.analysisInProgress) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(color = Color(0xFF8ED9FF))
                Text(
                    text = state.phase.progressLabel(),
                    color = Color.White
                )
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("github_intelligence_cancel")
            ) {
                Text(
                    text = "Analyse abbrechen",
                    maxLines = 1
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStart,
                    enabled = state.canStart,
                    modifier = Modifier.testTag("github_intelligence_start")
                ) {
                    Text("Repository analysieren")
                }
            }
        }
    }
}

@Composable
private fun SelectionMenu(
    label: String,
    value: String,
    options: List<Pair<String, () -> Unit>>,
    enabled: Boolean,
    testTag: String
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelMedium
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            ) {
                Text(value)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (text, action) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            action()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: GitHubImprovementProposal,
    canCreatePlan: Boolean,
    onPreparePlan: () -> Unit
) {
    var evidenceExpanded by rememberSaveable(proposal.id) { mutableStateOf(false) }
    var pathsExpanded by rememberSaveable(proposal.id) { mutableStateOf(false) }
    var changesExpanded by rememberSaveable(proposal.id) { mutableStateOf(false) }
    var testsExpanded by rememberSaveable(proposal.id) { mutableStateOf(false) }
    var limitationsExpanded by rememberSaveable(proposal.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("github_intelligence_proposal"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = proposal.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(proposal.category.label(), style = MaterialTheme.typography.labelMedium)
            Text(
                text = "Nutzen: ${proposal.benefit.label()} · Risiko: ${proposal.risk.label()} · " +
                    "Aufwand: ${proposal.effort.label()} · Konfidenz: ${proposal.confidence.label()}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(proposal.summary)
            ExpandableProposalList(
                title = "Evidenz",
                entries = proposal.evidence.map { "${it.path}: ${it.observation}" },
                expanded = evidenceExpanded,
                onToggle = { evidenceExpanded = !evidenceExpanded }
            )
            ExpandableProposalList(
                title = "Betroffene Dateien",
                entries = proposal.affectedPaths,
                expanded = pathsExpanded,
                onToggle = { pathsExpanded = !pathsExpanded }
            )
            ExpandableProposalList(
                title = "Empfohlene Änderung",
                entries = proposal.suggestedChanges,
                expanded = changesExpanded,
                onToggle = { changesExpanded = !changesExpanded }
            )
            ExpandableProposalList(
                title = "Testplan",
                entries = proposal.testPlan,
                expanded = testsExpanded,
                onToggle = { testsExpanded = !testsExpanded }
            )
            ExpandableProposalList(
                title = "Einschränkungen",
                entries = proposal.limitations,
                expanded = limitationsExpanded,
                onToggle = { limitationsExpanded = !limitationsExpanded }
            )
            if (canCreatePlan) {
                Button(
                    onClick = onPreparePlan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("github_intelligence_prepare_plan_${proposal.id}")
                ) {
                    Text("Umsetzungsplan vorbereiten", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ExpandableProposalList(
    title: String,
    entries: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (expanded) "$title ausblenden" else "$title anzeigen",
                maxLines = 1
            )
        }
        if (expanded) {
            entries.forEach { entry ->
                Text("• $entry", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AgentDraftPrPlanCard(
    state: GitHubIntelligenceUiState,
    plan: AgentImplementationPlan,
    onToggleApproval: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onOpenDraftPullRequest: (String) -> Unit
) {
    var submissionInvoked by remember(plan.planId) { mutableStateOf(false) }
    InfoCard(title = "Geprüfter Umsetzungsplan") {
        Column(
            modifier = Modifier.testTag("github_intelligence_draft_plan"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(plan.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Repository: ${plan.repository.owner}/${plan.repository.name}",
                color = Color.White
            )
            Text("Basis-Ref: ${plan.baseRef}", color = Color.White)
            Text("Basis-Commit: ${plan.baseCommitSha}", color = Color.White)
            Text("Agenten-Branch: ${plan.branchName}", color = Color.White)
            Text("Risiko: ${plan.risk.label()}", color = Color.White)
            PlanList(
                title = "Belegpfade",
                entries = plan.evidencePaths,
                entryTestTagPrefix = "github_intelligence_draft_evidence_path"
            )
            PlanList(
                title = "Betroffene Dateien",
                entries = plan.affectedPaths,
                entryTestTagPrefix = "github_intelligence_draft_affected_path"
            )
            PlanList("Änderungsschritte", plan.changeSteps)
            PlanList(
                "Validierungen",
                plan.validationSteps.map(AgentValidationId::label)
            )
            PlanList("Einschränkungen", plan.limitations)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2E4A61)
            ) {
                Text(
                    text = "BamaFlow führt keine Änderungen lokal aus. Ein freigegebener Auftrag " +
                        "verwendet ausschließlich einen isolierten Agenten-Branch. Es wird " +
                        "ausschließlich ein Draft Pull Request erstellt. Kein Merge und kein " +
                        "direkter Push auf main.",
                    modifier = Modifier.padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.draftPr.explicitApprovalChecked,
                    onCheckedChange = onToggleApproval,
                    enabled = state.draftPr.canChangeApproval,
                    modifier = Modifier.testTag("github_intelligence_draft_approval")
                )
                Text(
                    text = "Ich habe Repository, Basis-Commit, Pfade und Prüfplan kontrolliert.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!state.draftPr.serverAvailable) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_intelligence_server_unavailable"),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF23445F)
                ) {
                    Text(
                        text = "Sicherer Agent-Service noch nicht verbunden",
                        modifier = Modifier.padding(12.dp),
                        color = Color.White
                    )
                }
            }
            state.draftPr.safeMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.testTag("github_intelligence_draft_message"),
                    color = Color.White
                )
            }
            if (state.draftPr.submissionInProgress) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF8ED9FF))
                    Text(
                        text = "Draft-PR-Auftrag wird sicher geprüft …",
                        color = Color.White
                    )
                }
            }
            if (state.draftPr.cancellationInProgress) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_intelligence_draft_cancelling"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF8ED9FF))
                    Text(
                        text = "Draft-PR-Auftrag wird sicher abgebrochen …",
                        color = Color.White
                    )
                }
            }
            Button(
                onClick = {
                    if (!submissionInvoked) {
                        submissionInvoked = true
                        onSubmit()
                    }
                },
                enabled = state.draftPr.canSubmit && !submissionInvoked,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("github_intelligence_draft_submit")
            ) {
                Text("Draft-PR-Auftrag freigeben", maxLines = 1)
            }
            if (state.draftPr.phase == AgentDraftPrUiPhase.PLAN_READY ||
                state.draftPr.phase == AgentDraftPrUiPhase.SUBMITTING ||
                state.draftPr.phase == AgentDraftPrUiPhase.CANCELLING
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.draftPr.cancellationInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("github_intelligence_draft_cancel")
                ) {
                    Text("Auftrag abbrechen", maxLines = 1)
                }
            }
            state.draftPr.serverStatus?.let { status ->
                Text(
                    text = status.label(),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val draftPullRequestUrl = state.draftPr.draftPullRequestUrl
            val draftPullRequestNumber = state.draftPr.draftPullRequestNumber
            if (state.draftPr.phase == AgentDraftPrUiPhase.DRAFT_PR_CREATED &&
                draftPullRequestUrl != null &&
                AgentDraftPrUrlPolicy.isAllowed(draftPullRequestUrl, draftPullRequestNumber)
            ) {
                OutlinedButton(
                    onClick = { onOpenDraftPullRequest(draftPullRequestUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("github_intelligence_draft_pr_link")
                ) {
                    Text("Draft Pull Request #$draftPullRequestNumber öffnen", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PlanList(
    title: String,
    entries: List<String>,
    entryTestTagPrefix: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        entries.forEachIndexed { index, entry ->
            Text(
                text = "• $entry",
                modifier = if (entryTestTagPrefix == null) {
                    Modifier
                } else {
                    Modifier.testTag("${entryTestTagPrefix}_$index")
                },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF172D45).copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

private fun GitHubAnalysisArea.label(): String = when (this) {
    GitHubAnalysisArea.ARCHITECTURE -> "Architektur"
    GitHubAnalysisArea.SECURITY -> "Sicherheit"
    GitHubAnalysisArea.ANDROID_UI_UX -> "Android UI/UX"
    GitHubAnalysisArea.DESKTOP -> "Desktop"
    GitHubAnalysisArea.SHARED_CORE -> "SharedCore"
    GitHubAnalysisArea.TESTS -> "Tests"
    GitHubAnalysisArea.PERFORMANCE -> "Performance"
    GitHubAnalysisArea.ACCESSIBILITY -> "Barrierefreiheit"
    GitHubAnalysisArea.DOCUMENTATION -> "Dokumentation"
    GitHubAnalysisArea.PROVIDER_SYSTEM -> "Provider-System"
    GitHubAnalysisArea.AGENTS_EXTENSIONS -> "Agenten/Extensions"
}

private fun GitHubIntelligencePhase.progressLabel(): String = when (this) {
    GitHubIntelligencePhase.LOADING_REPOSITORY -> "Repository wird sicher gelesen …"
    GitHubIntelligencePhase.BUILDING_CONTEXT -> "Analysekontext wird begrenzt aufgebaut …"
    GitHubIntelligencePhase.ANALYZING -> "Strukturierte Vorschläge werden erstellt …"
    GitHubIntelligencePhase.NO_RESULTS -> ""
    else -> ""
}

private fun GitHubProposalCategory.label(): String = when (this) {
    GitHubProposalCategory.ARCHITECTURE -> "Architektur"
    GitHubProposalCategory.SECURITY -> "Sicherheit"
    GitHubProposalCategory.ANDROID_UI_UX -> "Android UI/UX"
    GitHubProposalCategory.DESKTOP -> "Desktop"
    GitHubProposalCategory.SHARED_CORE -> "SharedCore"
    GitHubProposalCategory.TESTS -> "Tests"
    GitHubProposalCategory.PERFORMANCE -> "Performance"
    GitHubProposalCategory.ACCESSIBILITY -> "Barrierefreiheit"
    GitHubProposalCategory.DOCUMENTATION -> "Dokumentation"
    GitHubProposalCategory.PROVIDER_SYSTEM -> "Provider-System"
    GitHubProposalCategory.AGENTS_EXTENSIONS -> "Agenten/Extensions"
}

private fun GitHubProposalBenefit.label(): String = when (this) {
    GitHubProposalBenefit.LOW -> "niedrig"
    GitHubProposalBenefit.MEDIUM -> "mittel"
    GitHubProposalBenefit.HIGH -> "hoch"
}

private fun GitHubProposalRisk.label(): String = when (this) {
    GitHubProposalRisk.LOW -> "niedrig"
    GitHubProposalRisk.MEDIUM -> "mittel"
    GitHubProposalRisk.HIGH -> "hoch"
}

private fun GitHubProposalEffort.label(): String = when (this) {
    GitHubProposalEffort.SMALL -> "klein"
    GitHubProposalEffort.MEDIUM -> "mittel"
    GitHubProposalEffort.LARGE -> "groß"
}

private fun GitHubProposalConfidence.label(): String = when (this) {
    GitHubProposalConfidence.LOW -> "niedrig"
    GitHubProposalConfidence.MEDIUM -> "mittel"
    GitHubProposalConfidence.HIGH -> "hoch"
}

private fun AgentValidationId.label(): String = when (this) {
    AgentValidationId.SHARED_CORE_TEST -> "SharedCore-Tests"
    AgentValidationId.ANDROID_UNIT_TEST -> "Android-Unit-Tests"
    AgentValidationId.ANDROID_COMPILE -> "Android-Kompilierung"
    AgentValidationId.ANDROID_ASSEMBLE -> "Android-Debug-Build"
    AgentValidationId.DESKTOP_COMPILE -> "Desktop-Kompilierung"
    AgentValidationId.DESKTOP_TEST -> "Desktop-Tests"
    AgentValidationId.DIFF_CHECK -> "Git-Diff-Prüfung"
}

private fun AgentDraftPrStatus.label(): String = when (this) {
    AgentDraftPrStatus.NOT_STARTED -> "Noch nicht gestartet"
    AgentDraftPrStatus.PLAN_READY -> "Plan bereit"
    AgentDraftPrStatus.AWAITING_APPROVAL -> "Freigabe ausstehend"
    AgentDraftPrStatus.DRY_RUN_VALIDATING -> "Sicherheitsprüfung läuft"
    AgentDraftPrStatus.READY_FOR_SERVER_SUBMISSION -> "Bereit zur Übermittlung"
    AgentDraftPrStatus.SERVER_ACCEPTED -> "Auftrag sicher angenommen"
    AgentDraftPrStatus.BRANCH_CREATED -> "Isolierter Agenten-Branch erstellt"
    AgentDraftPrStatus.CHANGES_APPLIED -> "Änderungen im Agenten-Branch vorbereitet"
    AgentDraftPrStatus.TESTS_RUNNING -> "Prüfungen laufen"
    AgentDraftPrStatus.TESTS_PASSED -> "Prüfungen bestanden"
    AgentDraftPrStatus.DRAFT_PR_CREATED -> "Draft Pull Request erstellt"
    AgentDraftPrStatus.TESTS_FAILED -> "Prüfungen fehlgeschlagen"
    AgentDraftPrStatus.CANCELLED -> "Auftrag abgebrochen"
    AgentDraftPrStatus.FAILED -> "Auftrag fehlgeschlagen"
}
