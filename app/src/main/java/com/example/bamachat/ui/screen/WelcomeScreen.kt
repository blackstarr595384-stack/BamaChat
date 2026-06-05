package com.example.bamachat.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WelcomeScreen(
    isAuthenticated: Boolean,
    onOpenChat: () -> Unit,
    onOpenAuth: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onOpenHelp: () -> Unit
) {
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF08111F),
                        Color(0xFF132844),
                        Color(0xFF18385E)
                    )
                )
            )
    ) {
        WelcomeBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                ) + slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            ) {
                WelcomeHero()
            }

            Spacer(Modifier.height(18.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 650,
                        delayMillis = 90,
                        easing = FastOutSlowInEasing
                    )
                ) + slideInVertically(
                    initialOffsetY = { it / 8 },
                    animationSpec = tween(
                        durationMillis = 650,
                        delayMillis = 90,
                        easing = FastOutSlowInEasing
                    )
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WelcomeEyebrow("AI Workspace OS")
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "BamaChat",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Autonomer KI-Arbeitsraum: Chat, Personas, Tools, Agenten, Kollaboration – in einer Plattform.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFD8E4FF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.92f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 700,
                        delayMillis = 180,
                        easing = FastOutSlowInEasing
                    )
                ) + slideInVertically(
                    initialOffsetY = { it / 7 },
                    animationSpec = tween(
                        durationMillis = 700,
                        delayMillis = 180,
                        easing = FastOutSlowInEasing
                    )
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFF213857).copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            WelcomeFeaturePill("KI-Agenten")
                            WelcomeFeaturePill("MCP-Tools")
                            WelcomeFeaturePill("Kollaboration")
                            WelcomeFeaturePill("Workspaces")
                        }

                        if (isAuthenticated) {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                onClick = onOpenChat,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB9CCFF),
                                    contentColor = Color(0xFF10233F)
                                )
                            ) {
                                Text("Zum BamaHub", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                onClick = onContinueAsGuest,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB9CCFF),
                                    contentColor = Color(0xFF10233F)
                                )
                            ) {
                                Text("Sofort als Gast starten", fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                onClick = onOpenAuth,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                            ) {
                                Text("Anmelden oder Registrieren", color = Color.White.copy(alpha = 0.92f))
                            }
                        }

                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            onClick = onOpenHelp,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                        ) {
                            Text("Hilfe & Anleitung", color = Color.White.copy(alpha = 0.92f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeBackdrop() {
    val transition = rememberInfiniteTransition(label = "welcome_backdrop")
    val driftA by transition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftA"
    )
    val driftB by transition.animateFloat(
        initialValue = 16f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftB"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 44.dp, y = (-22).dp)
                .size(220.dp)
                .graphicsLayer(translationX = driftA, translationY = driftB)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF8FB2FF).copy(alpha = 0.28f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-78).dp, y = 120.dp)
                .size(260.dp)
                .graphicsLayer(translationX = driftB, translationY = driftA)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF4D86D8).copy(alpha = 0.2f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun WelcomeHero() {
    val transition = rememberInfiniteTransition(label = "welcome_hero")
    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    val corePulse by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )
    val chipFloatA by transition.animateFloat(
        initialValue = -12f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chipFloatA"
    )
    val chipFloatB by transition.animateFloat(
        initialValue = 8f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chipFloatB"
    )
    val chipFloatC by transition.animateFloat(
        initialValue = -6f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chipFloatC"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(286.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(248.dp)
                .graphicsLayer(scaleX = corePulse, scaleY = corePulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF7CA9FF).copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Canvas(
            modifier = Modifier
                .size(248.dp)
                .graphicsLayer(rotationZ = ringRotation)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val orbitRadius = size.minDimension * 0.35f
            val pointRadius = 10f
            val orbitAngles = listOf(-80f, 28f, 156f)

            orbitAngles.forEach { angle ->
                val radians = Math.toRadians(angle.toDouble())
                val target = Offset(
                    x = center.x + cos(radians).toFloat() * orbitRadius,
                    y = center.y + sin(radians).toFloat() * orbitRadius
                )
                drawLine(
                    color = Color(0xFFAEC6FF).copy(alpha = 0.34f),
                    start = center,
                    end = target,
                    strokeWidth = 3f
                )
                drawCircle(
                    color = Color(0xFFD8E4FF).copy(alpha = 0.95f),
                    radius = pointRadius,
                    center = target
                )
            }

            drawCircle(
                color = Color(0xFFB8CDFF).copy(alpha = 0.2f),
                radius = orbitRadius + 26f,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = orbitRadius + 48f,
                style = Stroke(width = 2f)
            )
        }

        Surface(
            modifier = Modifier
                .size(122.dp)
                .graphicsLayer(scaleX = corePulse, scaleY = corePulse),
            shape = CircleShape,
            color = Color(0xFF0F2038),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFB7CAFF),
                                Color(0xFF6A90E8),
                                Color(0xFF153055)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "B",
                        color = Color(0xFF11223D),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        text = "AI",
                        color = Color(0xFF18345D),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        OrbitBadge(
            label = "Chat",
            modifier = Modifier
                .offset(x = (-88).dp, y = (-56).dp)
                .graphicsLayer(translationY = chipFloatA)
        )
        OrbitBadge(
            label = "Persona",
            modifier = Modifier
                .offset(x = 90.dp, y = (-18).dp)
                .graphicsLayer(translationY = chipFloatB)
        )
        OrbitBadge(
            label = "Live",
            modifier = Modifier
                .offset(x = (-6).dp, y = 96.dp)
                .graphicsLayer(translationY = chipFloatC)
        )
    }
}

@Composable
private fun WelcomeEyebrow(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color(0xFFD8E4FF),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun WelcomeFeaturePill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB9CCFF))
            )
            Text(
                text = label,
                color = Color(0xFFE1EBFF),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun OrbitBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF213857).copy(alpha = 0.84f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFBCD0FF))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.94f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
