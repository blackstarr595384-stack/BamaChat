package com.example.bamachat.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.ui.theme.AppDesignPalette
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPink
import com.example.bamachat.ui.theme.NeonGreen
import com.example.bamachat.ui.theme.NeonBlue
import com.example.bamachat.ui.theme.SurfaceDarkCard
import com.example.bamachat.ui.theme.SurfaceDarkElevated

private data class DashboardCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color,
    val accentColor: Color,
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
    onOpenKnowledgeGraph: () -> Unit,
    onOpenHelp: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenMenu: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenProfile: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenHermesCodingAssistant: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") simpleModeEnabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") onToggleSimpleMode: (Boolean) -> Unit = {}
) {
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }

    val chatCard = DashboardCard("Chat", "Chat-Verlauf und laufende Gespräche", Icons.AutoMirrored.Filled.Chat, NeonPurple, Color(0xFF7C4DFF), NeonPurple, onClick = onOpenChat)

    val workspaceCards = listOf(
        DashboardCard("Arbeitsbereiche", "Bereiche, Filter, Automationen", Icons.Default.Folder, Color(0xFFFF6B35), Color(0xFFCC4400), Color(0xFFFF6B35), onClick = onOpenWorkspaceSettings),
        DashboardCard("Mini-Apps", "Tools, Browser, Spiele", Icons.Default.Extension, NeonGreen, Color(0xFF008844), NeonGreen, onClick = onOpenMiniApps),
        DashboardCard("Agent Hub", "Persona, Regeln, Assistenten", Icons.Default.Psychology, NeonBlue, Color(0xFF0066CC), NeonCyan, onClick = onOpenAgentHub),
        DashboardCard("AI Extensions", "Plugins für erweiterte Funktionen", Icons.Default.Tune, Color(0xFFFF6B35), Color(0xFFCC4400), Color(0xFFFF6B35), onClick = onOpenExtensions)
    )

    val advancedCards = listOf(
        DashboardCard("Wissensgraph", "Themen und Verbindungen", Icons.Default.AccountTree, Color(0xFF00BFA5), Color(0xFF00796B), Color(0xFF00BFA5), onClick = onOpenKnowledgeGraph),
        DashboardCard("Real-Time Collab", "Gemeinsam chatten", Icons.Default.Groups, NeonPink, Color(0xFFAA0055), NeonPink, onClick = onOpenRealtimeCollab)
    )

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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Text("BamaHub", style = MaterialTheme.typography.headlineLarge, color = palette.heroTitle, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Wähle einen Bereich, um loszulegen", color = palette.textSecondary, fontSize = 14.sp)

            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp), color = SurfaceDarkCard.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Cloud, null, tint = NeonPurple.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Text(providerName, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp), color = SurfaceDarkCard.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Folder, null, tint = NeonCyan.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Text(if (activeWorkspaceName.isNotBlank()) activeWorkspaceName else "Standard-Bereich", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionChip(Icons.AutoMirrored.Filled.HelpCenter, "Hilfe", onOpenHelp, NeonCyan, modifier = Modifier.weight(1f))
                QuickActionChip(Icons.Default.Settings, "Einstellungen", onOpenSettings, NeonPurple, modifier = Modifier.weight(1f))
                QuickActionChip(Icons.Default.Palette, "Design", onOpenDesignSettings, NeonPink, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))

            DashboardCardItem(card = chatCard, palette = palette)

            Spacer(Modifier.height(20.dp))

            SectionHeader(title = "Arbeitsbereich", color = palette.textSecondary)
            Spacer(Modifier.height(10.dp))
            DashboardCardGroup(cards = workspaceCards, palette = palette)

            Spacer(Modifier.height(20.dp))

            SectionHeader(title = "Erweitert", color = palette.textSecondary)
            Spacer(Modifier.height(10.dp))
            DashboardCardGroup(cards = advancedCards, palette = palette)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDarkCard.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(16.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    color: Color
) {
    Text(
        text = title,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun DashboardCardGroup(
    cards: List<DashboardCard>,
    palette: AppDesignPalette,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useTwoColumns = maxWidth >= 600.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (useTwoColumns) {
                cards.chunked(2).forEach { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCards.forEach { card ->
                            DashboardCardItem(
                                card = card,
                                palette = palette,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowCards.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                cards.forEach { card ->
                    DashboardCardItem(
                        card = card,
                        palette = palette,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardCardItem(
    card: DashboardCard,
    palette: AppDesignPalette,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "cardScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp),
                spotColor = card.accentColor.copy(alpha = 0.25f),
                ambientColor = card.accentColor.copy(alpha = 0.1f))
            .clickable(interactionSource = interaction, indication = null, onClick = card.onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth().height(100.dp)
                .background(
                    Brush.horizontalGradient(colors = listOf(card.gradientStart.copy(alpha = 0.25f), card.gradientEnd.copy(alpha = 0.1f), SurfaceDarkCard)),
                    RoundedCornerShape(20.dp)
                )
                .border(BorderStroke(1.dp, card.accentColor.copy(alpha = 0.15f)), RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(14.dp), spotColor = card.accentColor.copy(alpha = 0.3f))
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(colors = listOf(card.gradientStart.copy(alpha = 0.4f), card.gradientEnd.copy(alpha = 0.2f)))
                        )
                        .border(BorderStroke(1.dp, card.accentColor.copy(alpha = 0.2f)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(card.icon, contentDescription = card.title, tint = card.accentColor, modifier = Modifier.size(22.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(card.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(card.subtitle, color = palette.textSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                }

                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(card.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = card.accentColor, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
