package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.theme.AppDesignPalette
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.util.McpServerManager
import com.example.bamachat.util.McpWorkflowManager
import com.example.bamachat.util.MonetizationConfig

private enum class SettingsMode { SIMPLE, ADVANCED }

private data class SettingsEntryItem(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val section: String? = null,
    val openProfile: Boolean = false
)

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    initialSection: String? = null,
    mcpServerManager: McpServerManager? = null,
    mcpWorkflowManager: McpWorkflowManager? = null
) {
    val provider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val design by settingsViewModel.uiDesignPreset.collectAsStateWithLifecycle()
    val activeWorkspaceName by settingsViewModel.activeWorkspaceName.collectAsStateWithLifecycle()
    val tier by settingsViewModel.subscriptionTier.collectAsStateWithLifecycle()
    val credits by settingsViewModel.creditsBalance.collectAsStateWithLifecycle()
    val simpleModeEnabled by settingsViewModel.simpleModeEnabled.collectAsStateWithLifecycle()

    var expandedSection by remember(initialSection) { mutableStateOf(initialSection) }
    var mode by remember(simpleModeEnabled) {
        mutableStateOf(if (simpleModeEnabled) SettingsMode.SIMPLE else SettingsMode.ADVANCED)
    }
    if (expandedSection != null) {
        SettingsDialog(
            viewModel = settingsViewModel,
            onDismiss = { expandedSection = null },
            initialSection = expandedSection,
            mcpServerManager = mcpServerManager,
            mcpWorkflowManager = mcpWorkflowManager
        )
    }
    val simpleEntries = listOf(
        SettingsEntryItem("Konto", "Profil, Anmeldung, E-Mail", Icons.Default.Person, openProfile = true),
        SettingsEntryItem("KI & Modelle", "Provider, API-Keys, Abo, Credits", Icons.Default.Tune, section = "ai"),
        SettingsEntryItem("Darstellung", "Farben, Layout, Chat-Anzeige", Icons.Default.Palette, section = "chat"),
        SettingsEntryItem("Sprache & Stimme", "TTS, Cloud-Voice, Sprachmodus", Icons.Default.GraphicEq, section = "voice"),
        SettingsEntryItem("Workspaces & Automationen", "Projekte, Schnellaktionen, Rollen", Icons.Default.Folder, section = "workspaces"),
        SettingsEntryItem("Datenschutz & Daten", "Sync, Bereinigung, App-Info", Icons.Default.Security, section = "data")
    )
    val advancedExtraEntries = listOf(
        SettingsEntryItem("Allgemein", "Sicherheit, Sprache, Benachrichtigung", Icons.Default.Settings, section = "general"),
        SettingsEntryItem("Agenten", "Persona-Profil, Regeln, Stil", Icons.Default.Psychology, section = "agents")
    )
    val entries = if (mode == SettingsMode.SIMPLE) simpleEntries else simpleEntries + advancedExtraEntries
    val palette = remember(design) { AppDesignSystem.paletteForStored(design) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.screenBgTop, palette.screenBgMid, palette.screenBgBottom)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = palette.heroTitle
                        )
                    }
                    Text(
                        text = "Einstellungen",
                        color = palette.heroTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.surface.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Hauptbereiche",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (mode == SettingsMode.SIMPLE)
                                "Einfach zeigt nur die wichtigsten Optionen."
                            else "Erweitert zeigt alle Bereiche für Feintuning.",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsModeToggle(
                            mode = mode,
                            palette = palette,
                            onModeChange = {
                                mode = it
                                settingsViewModel.setSimpleModeEnabled(it == SettingsMode.SIMPLE)
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        entries.forEach { item ->
                            SettingsEntry(
                                title = item.title,
                                subtitle = item.subtitle,
                                icon = item.icon,
                                palette = palette
                            ) {
                                if (item.openProfile) {
                                    onOpenProfile()
                                } else if (item.section != null) {
                                    expandedSection = item.section
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = palette.navIndicator.copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.surfaceBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Aktueller Status", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Provider: $provider",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Design: $design",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Workspace: $activeWorkspaceName",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Plan: ${MonetizationConfig.PlanTier.fromKey(tier).label} · Credits: $credits",
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Tippe oben auf einen Bereich, um direkt dorthin zu springen.",
                            color = palette.textSecondary.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                // P1-1 Design-Fix: BottomNav wird als Overlay gerendert; dieser Spacer hält
                // die letzten Einstellungen oberhalb von Navigationsleiste und BottomNav.
                Spacer(modifier = Modifier.height(104.dp))
            }
        }
    }
}

@Composable
private fun SettingsModeToggle(
    mode: SettingsMode,
    palette: AppDesignPalette,
    onModeChange: (SettingsMode) -> Unit
) {
    val selectedSimple = mode == SettingsMode.SIMPLE
    val selectedAdvanced = mode == SettingsMode.ADVANCED
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    // P1-3 Design-Fix: Modus-Kachel wird als Button mit Status vorgelesen.
                    role = Role.Button
                    contentDescription = if (selectedSimple) "Einfacher Modus ausgewählt" else "Einfachen Modus auswählen"
                }
                .clickable { onModeChange(SettingsMode.SIMPLE) },
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selectedSimple) palette.accent.copy(alpha = 0.72f) else palette.surfaceBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (selectedSimple) 0.2f else 0.1f),
                                palette.accent.copy(alpha = if (selectedSimple) 0.55f else 0.22f),
                                palette.surface.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = "Einfach",
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedSimple) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    role = Role.Button
                    contentDescription = if (selectedAdvanced) "Erweiterter Modus ausgewählt" else "Erweiterten Modus auswählen"
                }
                .clickable { onModeChange(SettingsMode.ADVANCED) },
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selectedAdvanced) palette.accentStrong.copy(alpha = 0.72f) else palette.surfaceBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (selectedAdvanced) 0.2f else 0.1f),
                                palette.accentStrong.copy(alpha = if (selectedAdvanced) 0.55f else 0.22f),
                                palette.surface.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = "Erweitert",
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedAdvanced) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: AppDesignPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                // P1-3 Design-Fix: kompletter Eintrag ist ein Button, Icons sind dekorativ.
                role = Role.Button
                contentDescription = "$title. $subtitle"
            }
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.accent
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = palette.textSecondary
        )
    }
}
