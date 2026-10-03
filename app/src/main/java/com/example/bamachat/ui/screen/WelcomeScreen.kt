package com.example.bamachat.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPink

@Composable
fun WelcomeScreen(
    isAuthenticated: Boolean,
    onOpenChat: () -> Unit,
    onOpenAuth: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    val floatUp by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatUp"
    )

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0D0D1A),
                        Color(0xFF14142A),
                        Color(0xFF1A1A2E)
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        // Animated background orbs
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-60).dp, y = (-200).dp)
                .graphicsLayer(alpha = glowPulse * 0.3f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            NeonPurple.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(x = 80.dp, y = 200.dp)
                .graphicsLayer(alpha = glowPulse * 0.2f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            NeonCyan.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // Logo icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer { translationY = floatUp }
                    .shadow(
                        elevation = 20.dp + 12.dp * glowPulse,
                        shape = CircleShape,
                        spotColor = NeonPurple.copy(alpha = 0.4f),
                        ambientColor = NeonPurple.copy(alpha = 0.2f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                NeonPurple,
                                Color(0xFF7C4DFF)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "B",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            // Title
            Text(
                "BamaFlow",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-1).sp
            )

            Text(
                "KI-Arbeitsplatz",
                fontSize = 16.sp,
                color = NeonPurple.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(16.dp))

            // Description
            Text(
                "Dein KI-Arbeitsplatz für Chat, Tools und Agents.\nLäuft auf Android — bald auch auf Windows/Desktop.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(Modifier.height(20.dp))

            // Action buttons (moved up for visibility)
            if (!isAuthenticated) {
                Button(
                    onClick = onOpenAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = NeonPurple.copy(alpha = 0.3f)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple
                    )
                ) {
                    Text(
                        "Mit Google anmelden",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onContinueAsGuest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            listOf(
                                NeonPurple.copy(alpha = 0.4f),
                                NeonCyan.copy(alpha = 0.2f)
                            )
                        )
                    )
                ) {
                    Text(
                        "Als Gast starten",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            } else {
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = NeonPurple.copy(alpha = 0.3f)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple
                    )
                ) {
                    Text(
                        "Zum BamaHub",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Feature highlights (moved below buttons)
            if (!isAuthenticated) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FeaturePill("💬 Chat & Agents", NeonPurple)
                    FeaturePill("🧩 Tools & Workflows", NeonCyan)
                    FeaturePill("☁️ Cloud & Collab", NeonPink)
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = onOpenHelp) {
                Text(
                    "Hilfe & Anleitung",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Version 1.0.1",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.2f)
            )
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeaturePill(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
