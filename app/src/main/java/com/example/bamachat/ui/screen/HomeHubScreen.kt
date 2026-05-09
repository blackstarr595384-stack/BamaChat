package com.example.bamachat.ui.screen

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
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenDesignSettings: () -> Unit,
    onOpenMiniApps: () -> Unit,
    onOpenAgentHub: () -> Unit,
    onOpenRealtimeCollab: () -> Unit,
    onOpenHelp: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenMenu: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenProfile: () -> Unit = {}
) {
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }
    val entries = listOf(
        HubEntry(
            title = "Chat",
            subtitle = "Schneller Einstieg in laufende Gespräche",
            icon = Icons.AutoMirrored.Filled.Chat,
            iconStartColor = Color(0xFF4D8DFF),
            iconEndColor = Color(0xFF77B6FF),
            onClick = onOpenChat
        ),
        HubEntry(
            title = "Agent Hub",
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
            title = "Realtime Collab",
            subtitle = "Live-Sessions mit Rollen und Präsenz",
            icon = Icons.Default.Groups,
            iconStartColor = Color(0xFFFF8D5C),
            iconEndColor = Color(0xFFFFB889),
            onClick = onOpenRealtimeCollab
        )
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
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeroHeader(providerName = providerName, palette = palette)

            StatusRow(
                providerName = providerName,
                designPreset = designPreset,
                onOpenProviderSettings = onOpenProviderSettings,
                onOpenDesignSettings = onOpenDesignSettings,
                palette = palette
            )

            FeatureGrid(entries = entries, palette = palette)
            QuickActions(onOpenHelp = onOpenHelp, onOpenCollab = onOpenRealtimeCollab, palette = palette)
            Spacer(modifier = Modifier.height(4.dp))
            UsageChip(palette = palette)
        }
    }
}

@Composable
private fun HeroHeader(providerName: String, palette: AppDesignPalette) {
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
    val pulse = rememberInfiniteTransition(label = "statusChipPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
        ),
        label = "statusChipGlow"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else glow,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "hub_chip_scale"
    )

    Surface(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .graphicsLayer(scaleX = scale, scaleY = scale)
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
                .padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        .padding(4.dp)
                        .size(13.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
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
                        contentDescription = title,
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
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun QuickActions(
    onOpenHelp: () -> Unit,
    onOpenCollab: () -> Unit,
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
            title = "Collab",
            icon = Icons.Default.Groups,
            onClick = onOpenCollab,
            modifier = Modifier.weight(1f),
            palette = palette,
            iconStartColor = Color(0xFF11B79F),
            iconEndColor = Color(0xFF59DBC6)
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
    val pulse = rememberInfiniteTransition(label = "actionPillPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(animation = tween(1200)),
        label = "actionPillGlow"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else glow,
        animationSpec = spring(dampingRatio = 0.88f),
        label = "hub_pill_scale"
    )

    Surface(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .graphicsLayer(scaleX = scale, scaleY = scale)
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        .padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
            text = "Free-Plan aktiv • Für mehr Volumen: Pro/Expert oder Credits",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
