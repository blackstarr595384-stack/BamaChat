package com.example.bamachat.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.theme.SurfaceDarkElevated

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
    modifier: Modifier = Modifier,
    attachedToComposer: Boolean = false,
    cornerRoundnessScale: Float = 1f,
    shadowIntensityScale: Float = 1f,
    surfaceOpacity: Float = 1f,
) {
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }

    val navItems = listOf(
        BottomNavItem("Hub", Icons.Default.Home, "home_hub"),
        BottomNavItem("Chat", Icons.AutoMirrored.Filled.Chat, "chat"),
        BottomNavItem("Tools", Icons.Default.Apps, "mini_apps"),
        BottomNavItem("Profil", Icons.Default.AccountCircle, "profile"),
        BottomNavItem("Einst.", Icons.Default.Settings, "settings")
    )

    val shape = RoundedCornerShape(28.dp)

    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(
                elevation = (24f * shadowIntensityScale).coerceIn(8f, 40f).dp,
                shape = shape,
                ambientColor = NeonPurple.copy(alpha = 0.15f),
                spotColor = NeonPurple.copy(alpha = 0.25f)
            ),
        shape = shape,
        color = SurfaceDarkElevated.copy(alpha = (0.95f * surfaceOpacity).coerceIn(0.6f, 1f)),
        tonalElevation = 8.dp
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF252540).copy(alpha = 0.9f),
                            Color(0xFF1A1A35).copy(alpha = 0.95f)
                        )
                    ),
                    shape = shape
                ),
            containerColor = Color.Transparent,
            contentColor = palette.textPrimary,
            tonalElevation = 0.dp,
            windowInsets = NavigationBarDefaults.windowInsets
        ) {
            navItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                    label = "navScale"
                )

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(item.route) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = palette.accent,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = palette.navUnselected,
                        unselectedTextColor = palette.navUnselected
                    ),
                    icon = {
                        Box(
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                                .size(44.dp)
                                .shadow(
                                    elevation = if (isSelected) 12.dp else 0.dp,
                                    shape = CircleShape,
                                    spotColor = palette.accent.copy(alpha = 0.4f),
                                    ambientColor = palette.accent.copy(alpha = 0.2f)
                                )
                                .clip(CircleShape)
                                .background(
                                    brush = if (isSelected) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                palette.accent.copy(alpha = 0.3f),
                                                palette.accent.copy(alpha = 0.15f)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.05f),
                                                Color.White.copy(alpha = 0.02f)
                                            )
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) palette.accent else palette.navUnselected
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 38.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                            )
                        }
                    },
                    label = {
                        if (isSelected) {
                            Text(
                                text = item.label,
                                color = palette.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    alwaysShowLabel = true
                )
            }
        }
    }
}
