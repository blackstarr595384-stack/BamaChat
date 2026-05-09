package com.example.bamachat.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Moderne UI Effekte: Glassmorphism, Shimmer, Blur, Particle Effects
 */

@Composable
fun GlassmorphicBox(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.1f),
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    blurRadius: Dp = 12.dp,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.12f),
                        backgroundColor.copy(alpha = 0.08f)
                    )
                )
            )
            .blur(blurRadius)
            .graphicsLayer {
                shape = RoundedCornerShape(cornerRadius)
                clip = true
            }
    ) {
        content()
    }
}

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Gray.copy(alpha = 0.2f),
    shimmerColor: Color = Color.White.copy(alpha = 0.3f),
    durationMillis: Int = 1500,
    cornerRadius: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .graphicsLayer {
                clip = true
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(translationX = shimmerX)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            shimmerColor,
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 300f
                    )
                )
        )
    }
}

@Composable
fun PulseEffect(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.1f),
    pulseColor: Color = Color.White.copy(alpha = 0.2f),
    cornerRadius: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha
            )
    )
}

@Composable
fun GradientShiftEffect(
    modifier: Modifier = Modifier,
    colorList: List<Color> = listOf(
        Color(0xFF6366F1),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color(0xFF6366F1)
    ),
    durationMillis: Int = 3000,
    cornerRadius: Dp = 16.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradientShift")
    val gradientRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientRotation"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.horizontalGradient(colors = colorList)
            )
            .graphicsLayer(rotationZ = gradientRotation)
    )
}

@Composable
fun BorderGlowEffect(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    glowColor: Color = Color.Cyan.copy(alpha = 0.6f),
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "borderGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .graphicsLayer {
                shadowElevation = 12f * glowAlpha
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(glowColor.copy(alpha = 0.1f * glowAlpha))
        )
        content()
    }
}

@Composable
fun FloatingParticleEffect(
    modifier: Modifier = Modifier,
    particleColor: Color = Color.White.copy(alpha = 0.3f),
    particleCount: Int = 5
) {
    Box(modifier = modifier) {
        repeat(particleCount) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "particle_$index")
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -100f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000 + index * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "particleY_$index"
            )
            val offsetX by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (index * 20 - 50).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(2000 + index * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "particleX_$index"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000 + index * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "particleAlpha_$index"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer(
                        translationY = offsetY,
                        translationX = offsetX,
                        alpha = alpha
                    )
                    .background(particleColor, RoundedCornerShape(50))
            ) {
                Box(Modifier.matchParentSize())
            }
        }
    }
}

@Composable
fun FadeInOutEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    durationMillis: Int = 1500
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fadeInOut")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fadeAlpha"
    )

    Box(modifier = modifier.graphicsLayer(alpha = alpha)) {
        content()
    }
}
