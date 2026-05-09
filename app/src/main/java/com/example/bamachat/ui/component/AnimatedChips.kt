package com.example.bamachat.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Moderne Chip und Button Komponenten mit Animationen
 */

@Composable
fun AnimatedChip(
    label: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.1f),
    textColor: Color = Color.White,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chipScale"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.3f else 0.1f,
        animationSpec = tween(300),
        label = "chipBgAlpha"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .scale(scale)
            .graphicsLayer(alpha = 0.99f),
        color = backgroundColor.copy(alpha = bgAlpha),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PulsingBadge(
    count: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFFF6B6B),
    textColor: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeScale"
    )

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ShimmeringBadge(
    label: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF3B82F6),
    textColor: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_badge")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .graphicsLayer(alpha = shimmerAlpha),
        color = backgroundColor,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun BouncingBadge(
    label: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF10B981),
    textColor: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_badge")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceY"
    )

    Box(
        modifier = modifier
            .graphicsLayer(translationY = offsetY)
            .clip(RoundedCornerShape(50))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
