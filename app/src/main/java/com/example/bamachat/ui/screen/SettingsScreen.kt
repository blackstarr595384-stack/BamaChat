package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPink
import com.example.bamachat.ui.theme.NeonGreen
import com.example.bamachat.ui.theme.NeonBlue
import com.example.bamachat.ui.theme.SurfaceDarkCard
import com.example.bamachat.ui.theme.SurfaceDarkElevated
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.util.MonetizationConfig

private enum class SettingsMode { SIMPLE, ADVANCED }

private data class SettingsEntryItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color,
    val section: String? = null,
    val openProfile: Boolean = false
)

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    cloudChatSyncUid: String? = null,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    initialSection: String? = null,
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenWorkspaceSettings: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") mcpServerManager: Any? = null,
    @Suppress("UNUSED_PARAMETER") mcpWorkflowManager: Any? = null
) {
    val provider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val design by settingsViewModel.uiDesignPreset.collectAsStateWithLifecycle()
    val activeWorkspaceName by settingsViewModel.activeWorkspaceName.collectAsStateWithLifecycle()
    val tier by settingsViewModel.subscriptionTier.collectAsStateWithLifecycle()
    val credits by settingsViewModel.creditsBalance.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var expandedSection by remember(initialSection) { mutableStateOf(initialSection) }
    var mode by remember { mutableStateOf(SettingsMode.SIMPLE) }
    if (expandedSection != null) {
        SettingsDialog(
            viewModel = settingsViewModel,
            cloudChatSyncUid = cloudChatSyncUid,
            onDismiss = { expandedSection = null },
            initialSection = expandedSection,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy
        )
    }

    val simpleEntries = listOf(
        SettingsEntryItem("Konto", "Profil, Anmeldung, E-Mail", Icons.Default.Person, NeonPurple, Color(0xFF7C4DFF), openProfile = true),
        SettingsEntryItem("KI & Modelle", "Provider, API-Keys, Abo, Credits", Icons.Default.Tune, NeonBlue, Color(0xFF0066CC), section = "ai"),
        SettingsEntryItem("Darstellung", "Farben, Layout, Chat-Anzeige", Icons.Default.Palette, NeonPink, Color(0xFFAA0055), section = "chat"),
        SettingsEntryItem("Sprache & Stimme", "TTS, Cloud-Voice, Sprachmodus", Icons.Default.GraphicEq, NeonGreen, Color(0xFF008844), section = "voice"),
        SettingsEntryItem("Arbeitsbereiche", "Bereiche, Filter, Automationen", Icons.Default.Folder, Color(0xFFFF6B35), Color(0xFFCC4400), section = "workspaces"),
        SettingsEntryItem("Datenschutz & Daten", "Sync, lokale Daten, Rechtliches", Icons.Default.Security, Color(0xFF00BFA5), Color(0xFF00796B), section = "data")
    )
    val advancedExtraEntries = listOf(
        SettingsEntryItem("Allgemein", "Sicherheit, Sprache, Benachrichtigung", Icons.Default.Settings, Color(0xFF6C63FF), Color(0xFF4A42D4), section = "general"),
        SettingsEntryItem("Agenten", "Persona-Profil, Regeln, Stil", Icons.Default.Psychology, Color(0xFF9C27B0), Color(0xFF6A1B9A), section = "agents")
    )
    val entries = if (mode == SettingsMode.SIMPLE) simpleEntries else simpleEntries + advancedExtraEntries

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0D0D1A),
                        Color(0xFF14142A),
                        Color(0xFF1A1A2E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Einstellungen",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Subscription info card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = NeonPurple.copy(alpha = 0.15f)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDarkElevated.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NeonPurple.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Abo \u2022 ${tier.uppercase()}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Credits: \u20AC${"%.2f".format(credits.toDouble())}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = { expandedSection = "ai" },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple
                        )
                    ) {
                        Text("Verwalten", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Settings entries
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    SettingsEntryCard(
                        item = entry,
                        onClick = {
                            if (entry.openProfile) onOpenProfile()
                            else if (entry.section == "workspaces") onOpenWorkspaceSettings()
                            else expandedSection = entry.section
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "BamaChat v1.0.1",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp
                )
                Text(
                    "KI-Edition",
                    color = Color.White.copy(alpha = 0.2f),
                    fontSize = 11.sp
                )
                Text(
                    "Entwickler: M.D Baldé",
                    color = Color.White.copy(alpha = 0.2f),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsEntryCard(
    item: SettingsEntryItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = item.gradientStart.copy(alpha = 0.1f)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDarkCard.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            item.gradientStart.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = item.gradientStart.copy(alpha = 0.2f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                item.gradientStart.copy(alpha = 0.2f),
                                item.gradientEnd.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .border(
                        androidx.compose.foundation.BorderStroke(
                            1.dp, item.gradientStart.copy(alpha = 0.15f)
                        ),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.gradientStart,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    item.subtitle,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
