package com.example.bamachat.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    modifier: Modifier = Modifier,
    attachedToComposer: Boolean = false,
    cornerRoundnessScale: Float = 1f,
    shadowIntensityScale: Float = 1f,
    surfaceOpacity: Float = 1f,
) {
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }
    val navContainer = palette.chatComposerBg.copy(alpha = (0.96f * surfaceOpacity).coerceIn(0.55f, 1f))
    val navBorder = palette.surfaceBorder.copy(alpha = 0.72f)
    val navIndicator = palette.chatNeutralControlBg.copy(alpha = 0.96f)
    val navSelected = palette.accent
    val navUnselected = palette.textSecondary.copy(alpha = 0.92f)

    val navItems = listOf(
        BottomNavItem("Home", Icons.Default.Home, "home_hub"),
        BottomNavItem("Chat", Icons.AutoMirrored.Filled.Chat, "chat"),
        BottomNavItem("Profil", Icons.Default.AccountCircle, "profile"),
        BottomNavItem("Optionen", Icons.Default.Settings, "settings")
    )

    val topRadius = (24f * cornerRoundnessScale).coerceIn(14f, 34f).dp
    val bottomRadius = ((if (attachedToComposer) 18f else 24f) * cornerRoundnessScale).coerceIn(10f, 34f).dp
    val navShape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius
    )

    if (attachedToComposer) {
        Surface(
            modifier = modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(
                    elevation = (10f * shadowIntensityScale).coerceIn(2f, 18f).dp,
                    shape = RoundedCornerShape(20.dp),
                    clip = false
                ),
            shape = RoundedCornerShape(20.dp),
            color = navContainer.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, navBorder.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                navItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.03f else 1f,
                        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                        label = "compactNavScale"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                // P1-3 Design-Fix: kompakte BottomNav-Items sind explizite Buttons.
                                role = Role.Button
                                contentDescription = item.label
                            }
                            .clickable { onNavigate(item.route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                                // P1-2 Design-Fix: jeder Navigationsbereich bietet mindestens 48dp Touch-Höhe.
                                .size(
                                    width = if (isSelected) 56.dp else 48.dp,
                                    height = 48.dp
                                )
                                .shadow(
                                    elevation = if (isSelected) 5.dp else 0.dp,
                                    shape = RoundedCornerShape(18.dp),
                                    spotColor = if (isSelected) navSelected.copy(alpha = 0.28f) else Color.Transparent
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.2f),
                                                navIndicator.copy(alpha = 0.85f),
                                                navIndicator.copy(alpha = 0.55f)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.08f),
                                                navIndicator.copy(alpha = 0.18f)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) navSelected.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
                                    ),
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) navSelected else navUnselected
                            )
                        }
                        Text(
                            text = item.label,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) navSelected else navUnselected
                        )
                    }
                }
            }
        }
        return
    }

    NavigationBar(
        windowInsets = NavigationBarDefaults.windowInsets,
        modifier = modifier
            .padding(
                start = 10.dp,
                end = 10.dp,
                top = if (attachedToComposer) 0.dp else 8.dp,
                bottom = if (attachedToComposer) 4.dp else 8.dp
            )
            .shadow(elevation = (16f * shadowIntensityScale).coerceIn(4f, 30f).dp, shape = navShape, clip = false)
            .clip(navShape)
            .border(BorderStroke(1.dp, navBorder), navShape)
            .background(navContainer),
        containerColor = navContainer,
        contentColor = navSelected,
        tonalElevation = 0.dp
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute == item.route
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1f,
                animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                label = "navItemScale"
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
                    Box(
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = if (isSelected) scale else 1f,
                                scaleY = if (isSelected) scale else 1f
                            )
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .shadow(
                                elevation = if (isSelected) 6.dp else 1.dp,
                                shape = CircleShape,
                                spotColor = if (isSelected) navSelected.copy(alpha = 0.35f) else Color.Transparent
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
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (isSelected) navSelected else navUnselected
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}
