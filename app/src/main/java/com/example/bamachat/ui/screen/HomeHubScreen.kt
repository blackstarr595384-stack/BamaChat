package com.example.bamachat.ui.screen

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.ui.theme.AppDesignPalette
import com.example.bamachat.ui.theme.AppDesignSystem

private data class HubEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconStartColor: Color,
    val iconEndColor: Color,
    val onClick: () -> Unit
)

@Composable
fun HomeHubScreen(
    providerName: String,
    designPreset: String,
    activeWorkspaceName: String,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenDesignSettings: () -> Unit,
    onOpenWorkspaceSettings: () -> Unit,
    onOpenMiniApps: () -> Unit,
    onOpenAgentHub: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenRealtimeCollab: () -> Unit,
    onOpenHermesCodingAssistant: () -> Unit,
    onOpenKnowledgeGraph: () -> Unit,
    onOpenHelp: () -> Unit,
    simpleModeEnabled: Boolean,
    onToggleSimpleMode: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenMenu: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenProfile: () -> Unit = {}
) {
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }
    val allEntries = listOf(
        HubEntry(
            title = "Chat",
            subtitle = "Schneller Einstieg in laufende Gespräche",
            icon = Icons.AutoMirrored.Filled.Chat,
            iconStartColor = Color(0xFF4D8DFF),
            iconEndColor = Color(0xFF77B6FF),
            onClick = onOpenChat
        ),
        HubEntry(
            title = "Hermes Coding Assistant",
            subtitle = "Code prüfen und Patch-Vorschläge vorbereiten",
            icon = Icons.Default.Psychology,
            iconStartColor = Color(0xFF2F7DFF),
            iconEndColor = Color(0xFF9CC4FF),
            onClick = onOpenHermesCodingAssistant
        ),
        HubEntry(
            title = "Agenten-Zentrale",
            subtitle = "Regeln, Persona-Stil und Agent-Setup",
            icon = Icons.Default.Psychology,
            iconStartColor = Color(0xFF8B6DFF),
            iconEndColor = Color(0xFFB79AFF),
            onClick = onOpenAgentHub
        ),
        HubEntry(
            title = "Mini-Apps",
            subtitle = "Tools, Browser, Doodle, 2048, Notizen",
            icon = Icons.Default.Extension,
            iconStartColor = Color(0xFF11B79F),
            iconEndColor = Color(0xFF59DBC6),
            onClick = onOpenMiniApps
        ),
        HubEntry(
            title = "KI-Erweiterungen",
            subtitle = "Plugins installieren und Berechtigungen steuern",
            icon = Icons.Default.Tune,
            iconStartColor = Color(0xFF6A8CFF),
            iconEndColor = Color(0xFF9AB6FF),
            onClick = onOpenExtensions
        ),
        HubEntry(
            title = "Live-Zusammenarbeit",
            subtitle = "Live-Sessions mit Rollen und Präsenz",
            icon = Icons.Default.Groups,
            iconStartColor = Color(0xFFFF8D5C),
            iconEndColor = Color(0xFFFFB889),
            onClick = onOpenRealtimeCollab
        ),
        HubEntry(
            title = "Workspaces",
            subtitle = "Projektkontext und Automationen",
            icon = Icons.Default.Settings,
            iconStartColor = Color(0xFF11B79F),
            iconEndColor = Color(0xFF8DE1D4),
            onClick = onOpenWorkspaceSettings
        ),
        HubEntry(
            title = "Wissensgraph",
            subtitle = "Verbindungen und Konzepte visualisieren",
            icon = Icons.Default.AccountTree,
            iconStartColor = Color(0xFF43C6AC),
            iconEndColor = Color(0xFF7FD7D0),
            onClick = onOpenKnowledgeGraph
        )
    )
    val simpleEntries = listOf(
        HubEntry(
            title = "Chat",
            subtitle = "Fragen stellen, Aufgaben lösen, Antworten erhalten",
            icon = Icons.AutoMirrored.Filled.Chat,
            iconStartColor = Color(0xFF4D8DFF),
            iconEndColor = Color(0xFF77B6FF),
            onClick = onOpenChat
        ),
        HubEntry(
            title = "Hermes Coding Assistant",
            subtitle = "Code prüfen und Vorschläge kopieren",
            icon = Icons.Default.Psychology,
            iconStartColor = Color(0xFF2F7DFF),
            iconEndColor = Color(0xFF9CC4FF),
            onClick = onOpenHermesCodingAssistant
        ),
        HubEntry(
            title = "Workspaces",
            subtitle = "Projektkontext und Notizen zentral halten",
            icon = Icons.Default.Settings,
            iconStartColor = Color(0xFF11B79F),
            iconEndColor = Color(0xFF8DE1D4),
            onClick = onOpenWorkspaceSettings
        ),
        HubEntry(
            title = "Mini-Apps",
            subtitle = "Schnelltools für Alltag und Recherche",
            icon = Icons.Default.Extension,
            iconStartColor = Color(0xFF11B79F),
            iconEndColor = Color(0xFF59DBC6),
            onClick = onOpenMiniApps
        ),
        HubEntry(
            title = "Einstellungen",
            subtitle = "Konto, Provider, Design und Datenschutz",
            icon = Icons.Default.Tune,
            iconStartColor = Color(0xFF6A8CFF),
            iconEndColor = Color(0xFF9AB6FF),
            onClick = onOpenSettings
        )
    )
    val entries = if (simpleModeEnabled) simpleEntries else allEntries

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.screenBgTop, palette.screenBgMid, palette.screenBgBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeroHeader(
                providerName = providerName,
                activeWorkspaceName = activeWorkspaceName,
                palette = palette
            )

            StatusRow(
                providerName = providerName,
                designPreset = designPreset,
                onOpenProviderSettings = onOpenProviderSettings,
                onOpenDesignSettings = onOpenDesignSettings,
                palette = palette
            )

            if (simpleModeEnabled) {
                SimpleModeCard(palette = palette)
            }

            FeatureGrid(entries = entries, palette = palette)
            QuickActions(
                onOpenHelp = onOpenHelp,
                simpleModeEnabled = simpleModeEnabled,
                onToggleSimpleMode = onToggleSimpleMode,
                palette = palette
            )
            Spacer(modifier = Modifier.height(4.dp))
            UsageChip(palette = palette)
            // P1-1 Design-Fix: zusätzlicher Abstand verhindert, dass die Overlay-BottomNav
            // auf Top-Level-Geräten Inhalte überdeckt.
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun HeroHeader(
    providerName: String,
    activeWorkspaceName: String,
    palette: AppDesignPalette
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = palette.heroBg,
        border = BorderStroke(1.dp, palette.heroBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "BamaChat",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.heroTitle,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Produktiver KI-Workspace",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.heroSubtitle
            )
            Text(
                text = "Aktiver Provider: $providerName",
                style = MaterialTheme.typography.labelMedium,
                color = palette.heroOverline
            )
            Text(
                text = "Workspace: $activeWorkspaceName",
                style = MaterialTheme.typography.labelMedium,
                color = palette.heroOverline
            )
        }
    }
}

@Composable
private fun StatusRow(
    providerName: String,
    designPreset: String,
    onOpenProviderSettings: () -> Unit,
    onOpenDesignSettings: () -> Unit,
    palette: AppDesignPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusChip(
            label = "Provider: $providerName",
            icon = Icons.Default.Tune,
            onClick = onOpenProviderSettings,
            modifier = Modifier.weight(1f),
            palette = palette,
            accent = palette.accentStrong
        )
        StatusChip(
            label = "Design: $designPreset",
            icon = Icons.Default.Palette,
            onClick = onOpenDesignSettings,
            modifier = Modifier.weight(1f),
            palette = palette,
            accent = palette.accent
        )
    }
}

@Composable
private fun SimpleModeCard(palette: AppDesignPalette) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.surfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = palette.accentStrong,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Einfacher Modus aktiv",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Du siehst die wichtigsten Bereiche für den schnellen Einstieg.",
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: AppDesignPalette,
    accent: androidx.compose.ui.graphics.Color
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val glow = if (animationsEnabled) {
        val pulse = rememberInfiniteTransition(label = "statusChipPulse")
        val animatedGlow by pulse.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
            ),
            label = "statusChipGlow"
        )
        animatedGlow
    } else {
        // P1-4 Design-Fix: keine Dauer-Pulse, wenn Android Animationen deaktiviert hat.
        1f
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else glow,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "hub_chip_scale"
    )

    Surface(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .semantics {
                // P1-3 Design-Fix: TalkBack erkennt den ganzen Chip als Button statt nur Text/Icon.
                role = Role.Button
                contentDescription = label
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                            accent.copy(alpha = 0.28f),
                            palette.surface.copy(alpha = 0.9f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .padding(5.dp)
                        .size(16.dp)
                )
            }
            Text(
                text = label,
                fontSize = 14.sp,
                color = palette.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FeatureGrid(entries: List<HubEntry>, palette: AppDesignPalette) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.chunked(2).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowEntries.forEach { entry ->
                    HubCard(
                        modifier = Modifier.weight(1f),
                        title = entry.title,
                        subtitle = entry.subtitle,
                        icon = entry.icon,
                        iconStartColor = entry.iconStartColor,
                        iconEndColor = entry.iconEndColor,
                        onClick = entry.onClick,
                        palette = palette
                    )
                }
                if (rowEntries.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HubCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconStartColor: Color,
    iconEndColor: Color,
    onClick: () -> Unit,
    palette: AppDesignPalette
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "hub_card_scale"
    )

    Surface(
        modifier = modifier
            .shadow(15.dp, RoundedCornerShape(18.dp))
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .semantics {
                // P1-3 Design-Fix: Card wird als ein Button angekündigt, Icon bleibt dekorativ.
                role = Role.Button
                contentDescription = "$title. $subtitle"
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (pressed) palette.surface.copy(alpha = 0.9f) else palette.surface,
        border = BorderStroke(1.dp, palette.surfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.size(42.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    iconStartColor.copy(alpha = 0.9f),
                                    iconEndColor.copy(alpha = 0.95f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = title,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = subtitle,
                color = palette.textSecondary,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun QuickActions(
    onOpenHelp: () -> Unit,
    simpleModeEnabled: Boolean,
    onToggleSimpleMode: (Boolean) -> Unit,
    palette: AppDesignPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionPill(
            title = "Hilfe",
            icon = Icons.AutoMirrored.Filled.HelpCenter,
            onClick = onOpenHelp,
            modifier = Modifier.weight(1f),
            palette = palette,
            iconStartColor = Color(0xFF4B8EFF),
            iconEndColor = Color(0xFF7EC1FF)
        )
        ActionPill(
            title = if (simpleModeEnabled) "Mehr Features" else "Einfach",
            icon = if (simpleModeEnabled) Icons.Default.Settings else Icons.Default.Tune,
            onClick = { onToggleSimpleMode(!simpleModeEnabled) },
            modifier = Modifier.weight(1f),
            palette = palette,
            iconStartColor = if (simpleModeEnabled) Color(0xFF8B6DFF) else Color(0xFF11B79F),
            iconEndColor = if (simpleModeEnabled) Color(0xFFB79AFF) else Color(0xFF59DBC6)
        )
    }
}

@Composable
private fun ActionPill(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palette: AppDesignPalette,
    iconStartColor: Color,
    iconEndColor: Color
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val glow = if (animationsEnabled) {
        val pulse = rememberInfiniteTransition(label = "actionPillPulse")
        val animatedGlow by pulse.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(animation = tween(1200)),
            label = "actionPillGlow"
        )
        animatedGlow
    } else {
        // P1-4 Design-Fix: keine Dauer-Pulse, wenn Android Animationen deaktiviert hat.
        1f
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else glow,
        animationSpec = spring(dampingRatio = 0.88f),
        label = "hub_pill_scale"
    )

    Surface(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .semantics {
                // P1-3 Design-Fix: Action-Pill ist für Screenreader ein einzelner Button.
                role = Role.Button
                contentDescription = title
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f),
                            iconStartColor.copy(alpha = 0.3f),
                            palette.surface.copy(alpha = 0.92f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    iconStartColor.copy(alpha = 0.9f),
                                    iconEndColor.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UsageChip(palette: AppDesignPalette) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = palette.navIndicator,
        border = BorderStroke(1.dp, palette.surfaceBorder)
    ) {
        Text(
            // P2-9 Design-Fix: neutraler Hinweis, damit der Home-Hub keine falsche Planstufe behauptet.
            text = "Plan & Credits findest du in Einstellungen → KI & Modelle",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
