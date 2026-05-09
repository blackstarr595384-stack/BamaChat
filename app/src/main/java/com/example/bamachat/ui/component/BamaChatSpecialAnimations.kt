package com.example.bamachat.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spezielle Animationen für BamaChat: Persona-Emojis, Feature-Cards, etc.
 */

@Composable
fun AnimatedPersonaEmoji(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val infiniteTransition = rememberInfiniteTransition(label = "personaEmoji")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emojiScale"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emojiRotation"
    )

    Text(
        text = emoji,
        fontSize = size.sp,
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationZ = rotation
            )
    )
}

@Composable
fun FeatureCardEnhanced(
    title: String,
    description: String,
    icon: String,
    backgroundColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isHovered: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.8f else 0.4f,
        animationSpec = tween(300),
        label = "cardShadow"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .scale(scale)
            .graphicsLayer {
                shadowElevation = 16f * shadowAlpha
            },
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 24.sp,
                        modifier = Modifier.scale(
                            if (isHovered) 1.2f else 1f
                        )
                    )
                }
            }
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun FloatingActionButtonEnhanced(
    icon: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab")
    val float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabFloat"
    )
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabGlow"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.8f)
                    )
                )
            )
            .graphicsLayer(translationY = float)
            .graphicsLayer {
                shadowElevation = 12f * glow
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 28.sp
        )
    }
}

@Composable
fun PulsingNotificationDot(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF6B6B)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "notification")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "notificationScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "notificationAlpha"
    )

    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
    )
}

@Composable
fun RatingStarAnimated(
    rating: Float,
    modifier: Modifier = Modifier,
    starColor: Color = Color(0xFFFFD700)
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { index ->
            val isFilled = index < rating.toInt() || (index == rating.toInt() && rating % 1 > 0.5)
            val infiniteTransition = rememberInfiniteTransition(label = "star_$index")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 100, easing = EaseInOutQuad),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "starScale_$index"
            )

            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (isFilled) starColor else starColor.copy(alpha = 0.2f),
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(
                        scaleX = if (isFilled) scale else 1f,
                        scaleY = if (isFilled) scale else 1f
                    )
            )
        }
    }
}

@Composable
fun ProgressRingAnimated(
    progress: Float,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Gray.copy(alpha = 0.2f),
    progressColor: Color = Color(0xFF6366F1),
    label: String = ""
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progressRing"
    )

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor,
            shape = CircleShape
        ) {}
        
        Surface(
            modifier = Modifier
                .fillMaxSize(animatedProgress)
                .align(Alignment.Center),
            color = progressColor.copy(alpha = 0.3f),
            shape = CircleShape
        ) {}
        
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
        }
    }
}
