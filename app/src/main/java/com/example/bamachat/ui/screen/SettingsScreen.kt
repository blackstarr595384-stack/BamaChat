package com.example.bamachat.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.data.cloud.AndroidChatSyncCoordinator.ChatSyncState
import com.example.bamachat.ui.component.settings.SettingsCategoryCard
import com.example.bamachat.ui.component.settings.SettingsNavigationRow
import com.example.bamachat.ui.component.settings.SettingsSectionTitle
import com.example.bamachat.ui.component.settings.SettingsTopBar
import com.example.bamachat.ui.component.settings.settingsScreenContentPadding
import com.example.bamachat.ui.settings.VoiceModeUiPolicy
import com.example.bamachat.ui.viewmodel.BamaVoiceViewModel
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.util.McpServerManager
import com.example.bamachat.util.McpWorkflowManager
import com.example.bamachat.util.MonetizationConfig

private data class SettingsOverviewCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val value: String? = null
)

@Composable
fun SettingsOverviewScreen(
    settingsViewModel: SettingsViewModel,
    voiceViewModel: BamaVoiceViewModel,
    cloudChatSyncUid: String? = null,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAiModels: () -> Unit,
    onOpenVoiceAudio: () -> Unit,
    onOpenWorkspaceSettings: () -> Unit,
    initialLegacySection: String? = null,
    onOpenPrivacyPolicy: () -> Unit = {},
    mcpServerManager: McpServerManager? = null,
    mcpWorkflowManager: McpWorkflowManager? = null
) {
    val provider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val activeWorkspaceName by settingsViewModel.activeWorkspaceName.collectAsStateWithLifecycle()
    val subscriptionTier by settingsViewModel.subscriptionTier.collectAsStateWithLifecycle()
    val voiceMode by settingsViewModel.voiceMode.collectAsStateWithLifecycle()
    val cloudPreferenceRevision by settingsViewModel.cloudChatSyncPreferenceRevision.collectAsStateWithLifecycle()
    val cloudRuntimeStatus by settingsViewModel.cloudChatSyncRuntimeStatus.collectAsStateWithLifecycle()
    var legacySection by rememberSaveable(initialLegacySection) {
        mutableStateOf(initialLegacySection)
    }

    val chatSyncEnabled = remember(cloudChatSyncUid, cloudPreferenceRevision) {
        settingsViewModel.isCloudChatSyncEnabledForUser(cloudChatSyncUid)
    }
    val syncSummary = remember(
        cloudChatSyncUid,
        chatSyncEnabled,
        cloudRuntimeStatus.uid,
        cloudRuntimeStatus.state
    ) {
        when {
            cloudChatSyncUid.isNullOrBlank() -> "Nur lokal · Anmeldung erforderlich"
            !chatSyncEnabled -> "Nur lokal"
            cloudRuntimeStatus.uid != cloudChatSyncUid -> "Synchronisierung aktiv"
            cloudRuntimeStatus.state == ChatSyncState.Pending -> "Synchronisierung ausstehend"
            cloudRuntimeStatus.state == ChatSyncState.Success -> "Zuletzt erfolgreich"
            cloudRuntimeStatus.state == ChatSyncState.Failed -> "Fehlgeschlagen · lokal sicher"
            else -> "Synchronisierung aktiv"
        }
    }
    val tierLabel = remember(subscriptionTier) {
        MonetizationConfig.PlanTier.fromKey(subscriptionTier).label
    }

    legacySection?.let { section ->
        SettingsDialog(
            viewModel = settingsViewModel,
            voiceViewModel = voiceViewModel,
            cloudChatSyncUid = cloudChatSyncUid,
            onDismiss = { legacySection = null },
            initialSection = section,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            mcpServerManager = mcpServerManager,
            mcpWorkflowManager = mcpWorkflowManager
        )
    }

    SettingsOverviewContent(
        tier = tierLabel,
        provider = provider,
        workspace = activeWorkspaceName.ifBlank { "Kein aktiver Arbeitsbereich" },
        syncStatus = syncSummary,
        voiceModeSummary = VoiceModeUiPolicy.overviewSummary(voiceMode),
        onBack = onBack,
        onOpenAccount = onOpenProfile,
        onOpenWorkspaces = onOpenWorkspaceSettings,
        onOpenGeneral = { legacySection = "general" },
        onOpenAiModels = onOpenAiModels,
        onOpenVoiceAudio = onOpenVoiceAudio,
        onOpenPrivacyData = { legacySection = "data" },
        onOpenAdvanced = { legacySection = "ai" }
    )
}

@Composable
internal fun SettingsOverviewContent(
    tier: String,
    provider: String,
    workspace: String,
    syncStatus: String,
    voiceModeSummary: String,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenAiModels: () -> Unit,
    onOpenVoiceAudio: () -> Unit,
    onOpenPrivacyData: () -> Unit,
    onOpenAdvanced: () -> Unit
) {
    val categories = listOf(
        SettingsOverviewCategory(
            id = "general",
            title = "Allgemein",
            description = "Erscheinungsbild, Sprache und App-Verhalten",
            icon = Icons.Default.Settings,
            accent = MaterialTheme.colorScheme.primary
        ),
        SettingsOverviewCategory(
            id = "ai",
            title = "KI und Modelle",
            description = "Anbieter, Modelle und automatische Auswahl",
            icon = Icons.Default.Tune,
            accent = Color(0xFF5C8DFF),
            value = provider
        ),
        SettingsOverviewCategory(
            id = "voice",
            title = "Sprache und Audio",
            description = "Sprachmodus, Mikrofon und Antwortstimme",
            icon = Icons.Default.GraphicEq,
            accent = Color(0xFFAE6CFF),
            value = voiceModeSummary
        ),
        SettingsOverviewCategory(
            id = "privacy",
            title = "Datenschutz und Daten",
            description = "Synchronisierung, Speicherung und Löschung",
            icon = Icons.Default.Security,
            accent = Color(0xFFFF6B7A),
            value = syncStatus
        ),
        SettingsOverviewCategory(
            id = "advanced",
            title = "Erweitert",
            description = "Experimente, Integrationen und Diagnose",
            icon = Icons.Default.Tune,
            accent = Color(0xFF8C93A8)
        )
    )

    Scaffold(
        modifier = Modifier.testTag("settings_overview_screen"),
        topBar = { SettingsTopBar(title = "Einstellungen", onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .testTag("settings_overview_list"),
            contentPadding = settingsScreenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") {
                SettingsStatusCard(
                    tier = tier,
                    provider = provider,
                    workspace = workspace,
                    syncStatus = syncStatus
                )
            }
            item(key = "account-title") {
                SettingsSectionTitle("KONTO UND PRODUKT")
            }
            item(key = "account") {
                SettingsNavigationRow(
                    title = "Konto und Abo",
                    description = "Profil, Anmeldung, Tarif und Credits",
                    value = tier,
                    icon = Icons.Default.Person,
                    onClick = onOpenAccount
                )
            }
            item(key = "workspaces") {
                SettingsNavigationRow(
                    title = "Arbeitsbereiche",
                    description = "Bereiche verwalten und aktiven Bereich wählen",
                    value = workspace,
                    icon = Icons.Default.Folder,
                    onClick = onOpenWorkspaces
                )
            }
            item(key = "categories-title") {
                SettingsSectionTitle("EINSTELLUNGEN")
            }
            items(categories, key = SettingsOverviewCategory::id) { category ->
                SettingsCategoryCard(
                    modifier = Modifier.testTag("settings_category_${category.id}"),
                    title = category.title,
                    description = category.description,
                    icon = category.icon,
                    accent = category.accent,
                    value = category.value,
                    onClick = when (category.id) {
                        "general" -> onOpenGeneral
                        "ai" -> onOpenAiModels
                        "voice" -> onOpenVoiceAudio
                        "privacy" -> onOpenPrivacyData
                        else -> onOpenAdvanced
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsStatusCard(
    tier: String,
    provider: String,
    workspace: String,
    syncStatus: String
) {
    val summary = "Tarif $tier. KI-Anbieter $provider. Arbeitsbereich $workspace. Synchronisierung $syncStatus"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = summary
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Auf einen Blick",
                style = MaterialTheme.typography.titleMedium
            )
            StatusLine(label = "Tarif", value = tier)
            StatusLine(label = "KI-Anbieter", value = provider)
            StatusLine(label = "Arbeitsbereich", value = workspace)
            StatusLine(label = "Synchronisierung", value = syncStatus)
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStackedLayout = maxWidth < 480.dp || LocalDensity.current.fontScale > 1.15f
        if (useStackedLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StatusLabel(label)
                StatusValue(value)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusLabel(label, Modifier.weight(1f))
                StatusValue(value, Modifier.weight(1.6f))
            }
        }
    }
}

@Composable
private fun StatusLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun StatusValue(value: String, modifier: Modifier = Modifier) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}
