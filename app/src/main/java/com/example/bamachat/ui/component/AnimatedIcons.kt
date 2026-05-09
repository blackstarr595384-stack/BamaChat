package com.example.bamachat.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Animierte Icons mit verschiedenen Effekten für lebendige UI
 */

@Composable
fun PulsingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingIcon")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsingScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsingAlpha"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint.copy(alpha = if (enabled) alpha else 0.5f),
        modifier = modifier
            .scale(if (enabled) scale else 1f)
            .graphicsLayer(alpha = if (enabled) alpha else 0.5f)
    )
}

@Composable
fun RotatingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    durationMillis: Int = 2000
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotatingIcon")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer(rotationZ = rotation)
    )
}

@Composable
fun BouncingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncingIcon")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bouncingOffset"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer(translationY = offset)
    )
}

@Composable
fun ShimmeringIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmeringIcon")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmeringAlpha"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint.copy(alpha = alpha),
        modifier = modifier
    )
}

@Composable
fun GlowingIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glowingIcon")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(modifier = modifier) {
        // Glow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(backgroundColor.copy(alpha = glowAlpha))
                .graphicsLayer(scaleX = glowScale, scaleY = glowScale)
        )
        // Icon layer
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun FlipIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    triggered: Boolean = false
) {
    val rotation by animateFloatAsState(
        targetValue = if (triggered) 360f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "flipRotation"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer(rotationY = rotation)
    )
}

@Composable
fun ScaleInOutIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scaleIcon"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.scale(scale)
    )
}
