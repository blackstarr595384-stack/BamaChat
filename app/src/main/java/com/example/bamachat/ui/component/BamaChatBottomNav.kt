package com.example.bamachat.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.bamachat.ui.theme.AppDesignSystem

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val badgeCount: Int = 0
)

@Composable
fun BamaChatBottomNav(
    currentRoute: String?,
    designPreset: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }
    val navContainer = palette.chatComposerBg.copy(alpha = 0.96f)
    val navBorder = palette.surfaceBorder.copy(alpha = 0.72f)
    val navIndicator = palette.chatNeutralControlBg.copy(alpha = 0.96f)
    val navSelected = palette.accent
    val navUnselected = palette.textSecondary.copy(alpha = 0.92f)

    val navItems = listOf(
        BottomNavItem("Home", Icons.Default.Home, "home_hub"),
        BottomNavItem("Chat", Icons.AutoMirrored.Filled.Chat, "chat"),
        BottomNavItem("Profil", Icons.Default.AccountCircle, "profile"),
        BottomNavItem("Einstellungen", Icons.Default.Settings, "settings")
    )

    NavigationBar(
        windowInsets = NavigationBarDefaults.windowInsets,
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, navBorder), RoundedCornerShape(24.dp))
            .background(navContainer),
        containerColor = navContainer,
        contentColor = navSelected,
        tonalElevation = 0.dp
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute == item.route
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.08f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
            )
            // Bounce effect beim Auswählen
            val bounceScale by animateFloatAsState(
                targetValue = if (isSelected) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "navItemBounce"
            )
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = navSelected,
                    selectedTextColor = navSelected,
                    indicatorColor = navIndicator,
                    unselectedIconColor = navUnselected,
                    unselectedTextColor = navUnselected
                ),
                icon = {
                    val iconPulse = rememberInfiniteTransition(label = "navGlassPulse")
                    val glow by iconPulse.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1100, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "navGlow"
                    )
                    Box(
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = if (isSelected) scale * bounceScale * glow else 1f,
                                scaleY = if (isSelected) scale * bounceScale * glow else 1f
                            )
                            .size(38.dp)
                            .shadow(
                                elevation = if (isSelected) 10.dp else 2.dp,
                                shape = CircleShape,
                                spotColor = if (isSelected) navSelected.copy(alpha = 0.55f) else Color.Transparent
                            )
                            .clip(CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isSelected) 0.22f else 0.14f),
                                        navIndicator.copy(alpha = if (isSelected) 0.75f else 0.45f),
                                        navIndicator.copy(alpha = if (isSelected) 0.42f else 0.24f)
                                    )
                                )
                            ),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(22.dp),
                            tint = if (isSelected) navSelected else navUnselected
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = if (isSelected) MaterialTheme.typography.labelMedium.fontSize else MaterialTheme.typography.labelSmall.fontSize
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}
