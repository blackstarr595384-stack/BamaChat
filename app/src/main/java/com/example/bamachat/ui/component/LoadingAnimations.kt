package com.example.bamachat.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Loading und Skeleton Komponenten mit modernen Animationen
 */

@Composable
fun AnimatedLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF6366F1),
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerRotation"
    )

    CircularProgressIndicator(
        modifier = modifier
            .size(size)
            .graphicsLayer(rotationZ = rotation),
        color = color,
        strokeWidth = 3.dp
    )
}

@Composable
fun DottedLoadingIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = Color(0xFF6366F1),
    dotSize: Dp = 8.dp,
    spacing: Dp = 6.dp,
    animationDuration: Int = 600
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot_$index")
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(animationDuration, delayMillis = index * 100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotOffset_$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
                    .graphicsLayer(translationY = offsetY)
            )
        }
    }
}

@Composable
fun WaveLoadingIndicator(
    modifier: Modifier = Modifier,
    waveColor: Color = Color(0xFF3B82F6),
    barWidth: Dp = 4.dp,
    barHeight: Dp = 24.dp,
    spacing: Dp = 4.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "wave_$index")
            val heightScale by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, delayMillis = index * 100, easing = EaseInOutQuad),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "waveScale_$index"
            )

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeight * heightScale)
                    .clip(RoundedCornerShape(50))
                    .background(waveColor)
            )
        }
    }
}

@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp,
    baseColor: Color = Color.Gray.copy(alpha = 0.1f),
    highlightColor: Color = Color.Gray.copy(alpha = 0.2f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonShimmer"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(baseColor)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(translationX = shimmerX)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            highlightColor,
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
fun PulseLoadingIndicator(
    label: String = "Lädt...",
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF6366F1).copy(alpha = 0.1f),
    textColor: Color = Color.White,
    pulseColor: Color = Color(0xFF6366F1)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_loading")
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
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun OrbitingLoadingIndicator(
    modifier: Modifier = Modifier,
    centerColor: Color = Color(0xFF6366F1),
    orbitColor: Color = Color(0xFF8B5CF6),
    size: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbiting")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer orbit
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(rotationZ = rotation),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(orbitColor)
                    .offset(y = 8.dp)
            )
        }

        // Center dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(centerColor)
        )
    }
}
