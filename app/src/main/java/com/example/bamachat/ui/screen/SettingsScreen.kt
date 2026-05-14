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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.viewmodel.SettingsViewModel
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
    initialSection: String? = null
) {
    val provider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val design by settingsViewModel.uiDesignPreset.collectAsStateWithLifecycle()
    val activeWorkspaceName by settingsViewModel.activeWorkspaceName.collectAsStateWithLifecycle()
    val tier by settingsViewModel.subscriptionTier.collectAsStateWithLifecycle()
    val credits by settingsViewModel.creditsBalance.collectAsStateWithLifecycle()

    var expandedSection by remember(initialSection) { mutableStateOf(initialSection) }
    var mode by remember { mutableStateOf(SettingsMode.SIMPLE) }
    if (expandedSection != null) {
        SettingsDialog(
            viewModel = settingsViewModel,
            onDismiss = { expandedSection = null },
            initialSection = expandedSection
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF7A2F20), Color(0xFF5B273F), Color(0xFF161C2E))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Einstellungen",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1A1A2D).copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Hauptbereiche",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (mode == SettingsMode.SIMPLE)
                            "Einfach zeigt nur die wichtigsten Optionen."
                        else "Advanced zeigt alle Bereiche für Feintuning.",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsModeToggle(
                        mode = mode,
                        onModeChange = { mode = it }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    entries.forEach { item ->
                        SettingsEntry(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF223248).copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Aktueller Status", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Provider: $provider",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Design: $design",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Workspace: $activeWorkspaceName",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Plan: ${MonetizationConfig.PlanTier.fromKey(tier).label} · Credits: $credits",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Tippe oben auf einen Bereich, um direkt dorthin zu springen.",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsModeToggle(
    mode: SettingsMode,
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
                .clickable { onModeChange(SettingsMode.SIMPLE) },
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = if (selectedSimple) 0.3f else 0.16f)
            )
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (selectedSimple) 0.2f else 0.1f),
                                Color(0xFF3B7BD4).copy(alpha = if (selectedSimple) 0.55f else 0.24f),
                                Color(0xFF1B2B42).copy(alpha = 0.9f)
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
                    tint = Color.White,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = "Einfach",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedSimple) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable { onModeChange(SettingsMode.ADVANCED) },
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = if (selectedAdvanced) 0.3f else 0.16f)
            )
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (selectedAdvanced) 0.2f else 0.1f),
                                Color(0xFF6A4BC7).copy(alpha = if (selectedAdvanced) 0.55f else 0.24f),
                                Color(0xFF1B2B42).copy(alpha = 0.9f)
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
                    tint = Color.White,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = "Advanced",
                    color = Color.White,
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFFD7A6)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.74f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f)
        )
    }
}
