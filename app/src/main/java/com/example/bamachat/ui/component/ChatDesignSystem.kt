package com.example.bamachat.ui.component
import androidx.compose.material3.Surface

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.SurfaceDarkCard
import com.example.bamachat.ui.viewmodel.ChatViewModel

enum class ChatDesignPreset {
    NOIR,
    SOLAR,
    CURRENT,
    GLASS,
    EDITORIAL,
    DASHBOARD;

    companion object {
        fun fromSetting(value: String): ChatDesignPreset = when (value) {
            "Glassmorphism Pro" -> GLASS
            "Editorial Bold" -> EDITORIAL
            "Neo Dashboard" -> DASHBOARD
            "Noir" -> NOIR
            "Solar" -> SOLAR
            else -> CURRENT
        }
    }
}

data class ChatDesignTokens(
    val titleSizeSp: Int = 22,
    val subtitleSizeSp: Int = 11,
    val listHorizontalPadding: Dp = 14.dp,
    val listVerticalSpacing: Dp = 10.dp,
    val headerShadow: Dp = 12.dp,
    val chipCornerRadius: Dp = 50.dp,
    val chipAlpha: Float = 0.16f,
    val bubbleSurfaceAlpha: Float = 0.86f,
    val inputCornerRadius: Dp = 28.dp,
    val userBubbleRoundness: Dp = 20.dp,
    val assistantBubbleRoundness: Dp = 20.dp,
    val bubbleMaxWidth: Dp = 300.dp,
    val bubbleShadow: Dp = 8.dp
)

fun designTokensFor(preset: ChatDesignPreset): ChatDesignTokens = when (preset) {
    ChatDesignPreset.GLASS -> ChatDesignTokens(
        titleSizeSp = 25, subtitleSizeSp = 11, listHorizontalPadding = 14.dp,
        listVerticalSpacing = 12.dp, headerShadow = 8.dp, chipCornerRadius = 24.dp,
        chipAlpha = 0.2f, bubbleSurfaceAlpha = 0.66f, inputCornerRadius = 30.dp,
        userBubbleRoundness = 18.dp, assistantBubbleRoundness = 18.dp,
        bubbleMaxWidth = 316.dp, bubbleShadow = 5.dp
    )
    ChatDesignPreset.EDITORIAL -> ChatDesignTokens(
        titleSizeSp = 28, subtitleSizeSp = 12, listHorizontalPadding = 16.dp,
        listVerticalSpacing = 14.dp, headerShadow = 10.dp, chipCornerRadius = 14.dp,
        chipAlpha = 0.14f, bubbleSurfaceAlpha = 0.9f, inputCornerRadius = 18.dp,
        userBubbleRoundness = 10.dp, assistantBubbleRoundness = 10.dp,
        bubbleMaxWidth = 340.dp, bubbleShadow = 8.dp
    )
    ChatDesignPreset.DASHBOARD -> ChatDesignTokens(
        titleSizeSp = 24, subtitleSizeSp = 11, listHorizontalPadding = 12.dp,
        listVerticalSpacing = 10.dp, headerShadow = 12.dp, chipCornerRadius = 10.dp,
        chipAlpha = 0.18f, bubbleSurfaceAlpha = 0.84f, inputCornerRadius = 16.dp,
        userBubbleRoundness = 12.dp, assistantBubbleRoundness = 12.dp,
        bubbleMaxWidth = 320.dp, bubbleShadow = 7.dp
    )
    ChatDesignPreset.NOIR -> ChatDesignTokens()
    ChatDesignPreset.SOLAR -> ChatDesignTokens()
    ChatDesignPreset.CURRENT -> ChatDesignTokens()
}

@Composable
fun EmptyChatState(themeColor: Color, persona: ChatViewModel.Persona, workspaceName: String = "") {
    val hasWorkspace = workspaceName.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .shadow(16.dp, CircleShape, spotColor = NeonPurple.copy(alpha = 0.3f))
                .background(
                    Brush.radialGradient(
                        listOf(NeonPurple, NeonPurple.copy(alpha = 0.3f))
                    ),
                    CircleShape
                )
                .border(1.5f.dp, NeonPurple.copy(alpha = 0.3f), CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(persona.emoji, fontSize = 48.sp)
        }
        Spacer(Modifier.height(24.dp))
        if (hasWorkspace) {
            Text(
                "Noch keine Unterhaltung",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Starte den ersten Chat in diesem Arbeitsbereich.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            Text(
                "Hallo! Ich bin ${persona.displayName}",
                color = Color.White,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Worüber möchtest du sprechen?",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun BlinkingDot(themeColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(themeColor.copy(alpha = alpha))
    )
}

@Composable
fun TypingIndicator(themeColor: Color, animated: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NeonPurple, NeonPurple.copy(alpha = 0.4f))
                    )
                )
                .border(1.dp, NeonPurple.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            color = SurfaceDarkCard,
            shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(3) { i ->
                    if (animated) {
                        val infiniteTransition = rememberInfiniteTransition(label = "dot$i")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.5f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                tween(600, delayMillis = i * 150),
                                RepeatMode.Reverse
                            ),
                            label = "scale$i"
                        )
                        Box(
                            modifier = Modifier
                                .size((7 * scale).dp)
                                .clip(CircleShape)
                                .background(NeonPurple.copy(alpha = scale))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(NeonPurple.copy(alpha = 0.7f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceVisualizer(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "progress"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { index ->
            val height = 4.dp + (16.dp * (progress * (index + 1) % 1f))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

fun sanitizeForSpeech(text: String): String = text
    .replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("`([^`]+)`"), "$1")
    .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "$1")
    .replace(Regex("https?://\\S+"), " ")
    .replace(Regex("Quellen \\(Live-Recherche\\):[\\s\\S]*"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

fun compactLabel(items: List<String>, maxItems: Int = 2): String {
    if (items.isEmpty()) return ""
    val unique = items.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (unique.isEmpty()) return ""
    return "${unique.take(maxItems).joinToString(", ")} +${unique.size - maxItems}"
}

fun splitSpeechChunks(text: String, maxChunkChars: Int = 220): List<String> {
    if (text.length <= maxChunkChars) return listOf(text)
    val chunks = mutableListOf<String>()
    val sentences = text.split(Regex("(?<=[.!?])\\s+"))
    val current = StringBuilder()
    for (sentence in sentences) {
        if (current.length + sentence.length > maxChunkChars && current.isNotEmpty()) {
            chunks.add(current.toString().trim())
            current.clear()
        }
        if (sentence.length > maxChunkChars) {
            sentence.chunked(maxChunkChars).forEach { chunk -> chunks.add(chunk.trim()) }
        } else {
            if (current.isNotEmpty()) current.append(" ")
            current.append(sentence)
        }
    }
    if (current.isNotEmpty()) chunks.add(current.toString().trim())
    return chunks
}
