package com.example.bamachat.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.ui.viewmodel.ChatViewModel
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.*

enum class ChatDesignPreset {
    CURRENT, GLASS, EDITORIAL, DASHBOARD;
    companion object {
        fun fromSetting(value: String): ChatDesignPreset = when (value) {
            "Glassmorphism Pro" -> GLASS; "Editorial Bold" -> EDITORIAL
            "Neo Dashboard" -> DASHBOARD; else -> CURRENT
        }
    }
}

data class ChatDesignTokens(
    val titleSizeSp: Int = 22, val subtitleSizeSp: Int = 11,
    val listHorizontalPadding: Dp = 14.dp, val listVerticalSpacing: Dp = 10.dp,
    val headerShadow: Dp = 12.dp, val chipCornerRadius: Dp = 50.dp,
    val chipAlpha: Float = 0.16f, val bubbleSurfaceAlpha: Float = 0.86f,
    val inputCornerRadius: Dp = 28.dp, val userBubbleRoundness: Dp = 20.dp,
    val assistantBubbleRoundness: Dp = 20.dp, val bubbleMaxWidth: Dp = 300.dp,
    val bubbleShadow: Dp = 8.dp
)

fun designTokensFor(preset: ChatDesignPreset): ChatDesignTokens = when (preset) {
    ChatDesignPreset.GLASS -> ChatDesignTokens(titleSizeSp = 25, subtitleSizeSp = 11,
        listHorizontalPadding = 14.dp, listVerticalSpacing = 12.dp, headerShadow = 8.dp,
        chipCornerRadius = 24.dp, chipAlpha = 0.2f, bubbleSurfaceAlpha = 0.66f,
        inputCornerRadius = 30.dp, userBubbleRoundness = 18.dp, assistantBubbleRoundness = 18.dp,
        bubbleMaxWidth = 316.dp, bubbleShadow = 5.dp)
    ChatDesignPreset.EDITORIAL -> ChatDesignTokens(titleSizeSp = 28, subtitleSizeSp = 12,
        listHorizontalPadding = 16.dp, listVerticalSpacing = 14.dp, headerShadow = 10.dp,
        chipCornerRadius = 14.dp, chipAlpha = 0.14f, bubbleSurfaceAlpha = 0.9f,
        inputCornerRadius = 18.dp, userBubbleRoundness = 10.dp, assistantBubbleRoundness = 10.dp,
        bubbleMaxWidth = 340.dp, bubbleShadow = 8.dp)
    ChatDesignPreset.DASHBOARD -> ChatDesignTokens(titleSizeSp = 24, subtitleSizeSp = 11,
        listHorizontalPadding = 12.dp, listVerticalSpacing = 10.dp, headerShadow = 12.dp,
        chipCornerRadius = 10.dp, chipAlpha = 0.18f, bubbleSurfaceAlpha = 0.84f,
        inputCornerRadius = 16.dp, userBubbleRoundness = 12.dp, assistantBubbleRoundness = 12.dp,
        bubbleMaxWidth = 320.dp, bubbleShadow = 7.dp)
    ChatDesignPreset.CURRENT -> ChatDesignTokens()
}

@Composable
fun ChatBubble(
    message: ChatMessage, onSpeak: (String) -> Unit, themeColor: Color,
    surfaceColor: Color, fontSize: Float, showTimestamps: Boolean, animateIn: Boolean,
    animationDelayMs: Int, designTokens: ChatDesignTokens
) {
    val isUser = message.isUser
    var visible by remember(message.id) { mutableStateOf(!animateIn) }
    LaunchedEffect(message.id, animateIn) {
        if (animateIn) { kotlinx.coroutines.delay(animationDelayMs.toLong()); visible = true }
    }
    val bubbleAlpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(320), label = "bubbleAlpha")
    val bubbleShift by animateDpAsState(targetValue = if (visible) 0.dp else 10.dp, animationSpec = tween(320), label = "bubbleShift")
    val bubbleShape = if (isUser) RoundedCornerShape(designTokens.userBubbleRoundness, 4.dp, designTokens.userBubbleRoundness, designTokens.userBubbleRoundness)
    else RoundedCornerShape(4.dp, designTokens.assistantBubbleRoundness, designTokens.assistantBubbleRoundness, designTokens.assistantBubbleRoundness)

    Row(modifier = Modifier.fillMaxWidth().offset(y = bubbleShift),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Brush.radialGradient(listOf(themeColor, themeColor.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            modifier = Modifier.widthIn(max = designTokens.bubbleMaxWidth).shadow(designTokens.bubbleShadow, bubbleShape),
            shape = bubbleShape, color = if (isUser) themeColor else surfaceColor.copy(alpha = designTokens.bubbleSurfaceAlpha)
        ) {
            Box(modifier = Modifier.padding(14.dp).graphicsLayer(alpha = bubbleAlpha)) {
                Column {
                    if (message.imageUrl != null) {
                        if (isUser) UploadedImageCard(message.imageUrl, message.text)
                        else GeneratedImageCard(message.imageUrl, message.text, themeColor)
                    } else if (isUser) {
                        Text(message.text, color = Color.White, fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.35f).sp, fontWeight = FontWeight.Medium)
                    } else {
                        if (message.text.isBlank()) BlinkingDot(themeColor)
                        else {
                            MarkdownText(markdown = message.text, style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFEDEEF0), fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp))
                            if (message.sources.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                SourcesSection(message.sources, message.webFetchedAtIso, themeColor)
                            }
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onSpeak(message.text) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.VolumeUp, "Vorlesen", tint = themeColor, modifier = Modifier.size(16.dp))
                                }
                                if (showTimestamps) {
                                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                        color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF35383D)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun UploadedImageCard(imageUrl: String, caption: String) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E2024)),
            contentAlignment = Alignment.Center) {
            AsyncImage(model = imageUrl, contentDescription = caption,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        if (caption.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(caption, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.height(4.dp))
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis())), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
    }
}

@Composable
private fun GeneratedImageCard(imageUrl: String, prompt: String, themeColor: Color) {
    var imageLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E2024)),
            contentAlignment = Alignment.Center) {
            if (!imageLoaded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = themeColor, strokeWidth = 3.dp)
                    Spacer(Modifier.height(8.dp)); Text("Generiere Bild...", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            AsyncImage(model = imageUrl, contentDescription = prompt,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))) },
                onSuccess = { imageLoaded = true }, onError = { imageLoaded = true })
        }
        if (prompt.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("Prompt: $prompt", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 2) }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Öffnen", tint = themeColor, modifier = Modifier.size(16.dp))
            }
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis())), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        }
    }
}

@Composable
fun EmptyChatState(themeColor: Color, persona: ChatViewModel.Persona) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(88.dp).shadow(20.dp, CircleShape, spotColor = themeColor)
            .background(Brush.radialGradient(listOf(themeColor, themeColor.copy(alpha = 0.3f))), CircleShape),
            contentAlignment = Alignment.Center) { Text(persona.emoji, fontSize = 44.sp) }
        Spacer(Modifier.height(20.dp))
        Text("Hallo! Ich bin dein ${persona.displayName}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Worüber möchtest du reden?", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
    }
}

@Composable
fun BlinkingDot(themeColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha")
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(themeColor.copy(alpha = alpha)))
}

@Composable
fun TypingIndicator(themeColor: Color, animated: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(32.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(themeColor, themeColor.copy(alpha = 0.5f)))),
            contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF272A2F)), shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)) {
            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    if (animated) {
                        val infiniteTransition = rememberInfiniteTransition(label = "dot$i")
                        val scale by infiniteTransition.animateFloat(initialValue = 0.5f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "scale$i")
                        Box(modifier = Modifier.size((6 * scale).dp).clip(CircleShape).background(themeColor.copy(alpha = scale)))
                    } else { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(themeColor.copy(alpha = 0.7f))) }
                }
            }
        }
    }
}

@Composable
fun VoiceVisualizer(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice")
    val progress by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse), label = "progress")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val height = 4.dp + (16.dp * (progress * (index + 1) % 1f))
            Box(modifier = Modifier.width(3.dp).height(height).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
fun SourcesSection(sources: List<com.example.bamachat.data.model.ChatSource>, fetchedAtIso: String?, themeColor: Color) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Live-Quellen", color = themeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        fetchedAtIso?.takeIf { it.isNotBlank() }?.let { Text("Stand: $it", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp) }
        sources.take(4).forEachIndexed { index, source ->
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Public, null, tint = themeColor, modifier = Modifier.size(12.dp))
                        Text("${index + 1}. ${source.title}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    }
                    if (source.snippet.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(source.snippet, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 3)
                    }
                }
            }
        }
    }
}

fun sanitizeForSpeech(text: String): String = text
    .replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("`([^`]+)`"), "$1")
    .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "$1")
    .replace(Regex("https?://\\S+"), " ")
    .replace(Regex("Quellen \\(Live-Recherche\\):[\\s\\S]*"), " ")
    .replace(Regex("\\s+"), " ").trim()

fun compactLabel(items: List<String>, maxItems: Int = 2): String {
    if (items.isEmpty()) return ""
    val unique = items.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (unique.isEmpty()) return ""
    if (unique.size <= maxItems) return unique.joinToString(", ")
    return "${unique.take(maxItems).joinToString(", ")} +${unique.size - maxItems}"
}
