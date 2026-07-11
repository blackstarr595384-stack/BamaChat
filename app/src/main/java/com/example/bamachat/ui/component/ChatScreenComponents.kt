package com.example.bamachat.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import com.example.bamachat.data.model.ChatMessage
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.theme.SurfaceDarkCard
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatBubble(
    message: ChatMessage,
    feedback: Boolean? = null,
    onFeedback: (Boolean) -> Unit = {},
    onSpeak: (String, String) -> Unit,
    isSpeaking: Boolean = false,
    showLiveSources: Boolean = true,
    themeColor: Color,
    surfaceColor: Color,
    fontSize: Float,
    showTimestamps: Boolean = true,
    animateIn: Boolean = true,
    animationDelayMs: Int = 0,
    designPreset: ChatDesignPreset,
    designTokens: ChatDesignTokens
) {
    val isUser = message.isUser
    var visible by remember(message.id) { mutableStateOf(!animateIn) }
    LaunchedEffect(message.id, animateIn) {
        if (animateIn) kotlinx.coroutines.delay(animationDelayMs.toLong())
        visible = true
    }
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f, animationSpec = tween(320), label = "bubbleAlpha"
    )
    val bubbleShift by animateDpAsState(
        targetValue = if (visible) 0.dp else 12.dp, animationSpec = tween(320), label = "bubbleShift"
    )

    val userShape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 4.dp,
        bottomStart = 20.dp, bottomEnd = 20.dp
    )
    val assistantShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 20.dp,
        bottomStart = 20.dp, bottomEnd = 20.dp
    )
    val bubbleShape = if (isUser) userShape else assistantShape

    val userBubbleGradient = Brush.horizontalGradient(
        colors = listOf(NeonPurple, Color(0xFF7C4DFF))
    )
    val assistantBubbleColor = SurfaceDarkCard.copy(alpha = 0.85f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, bubbleShift.roundToPx()) }
            .graphicsLayer(alpha = bubbleAlpha),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(NeonPurple.copy(alpha = 0.6f), NeonPurple.copy(alpha = 0.2f))
                        )
                    )
                    .border(1.dp, NeonPurple.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier
                .widthIn(max = designTokens.bubbleMaxWidth)
                .shadow(
                    elevation = if (isUser) 6.dp else 4.dp,
                    shape = bubbleShape,
                    spotColor = if (isUser) NeonPurple.copy(alpha = 0.2f) else Color.Transparent
                ),
            shape = bubbleShape,
            color = if (isUser) Color.Transparent else assistantBubbleColor
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (isUser) Modifier.background(userBubbleGradient, bubbleShape)
                        else Modifier
                    )
                    .border(
                        width = if (isUser) 0.dp else 1.dp,
                        color = Color.White.copy(alpha = 0.06f),
                        shape = bubbleShape
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        color = if (isUser) Color.White else Color.White.copy(alpha = 0.9f),
                        fontSize = fontSize.coerceIn(14f, 24f).sp,
                        lineHeight = (fontSize.coerceIn(14f, 24f) * 1.45f).sp
                    )

                    message.imageUrl?.let { url ->
                        Spacer(Modifier.height(8.dp))
                        GeneratedImageCard(
                            imageUrl = url,
                            prompt = message.text,
                            themeColor = themeColor
                        )
                    }

                    message.sources?.let { sources ->
                        if (sources.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            BubbleSourcesSection(
                                sources = sources,
                                fetchedAtIso = message.webFetchedAtIso,
                                themeColor = themeColor
                            )
                        }
                    }

                    if (showTimestamps) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val context = LocalContext.current
                            var copied by remember { mutableStateOf(false) }

                            // Copy Button
                            IconButton(
                                onClick = {
                                    val text = message.text
                                    if (text.isNotBlank()) {
                                        Log.d("ChatBubble", "Copy text length: ${text.length}")
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("BamaChat Nachricht", text))
                                            copied = true
                                            android.widget.Toast.makeText(context, "Nachricht kopiert.", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("ChatBubble", "Clipboard setPrimaryClip failed", e)
                                            android.widget.Toast.makeText(context, "Kopieren fehlgeschlagen: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Kopieren",
                                    tint = if (copied) Color.Green.copy(alpha = 0.8f) else themeColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Speak Button
                            IconButton(
                                onClick = { onSpeak(message.id, message.text) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Vorlesen",
                                    tint = if (isSpeaking) Color.Yellow.copy(alpha = 0.8f) else themeColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (!isUser) {
                                IconButton(onClick = { onFeedback(true) }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        Icons.Default.ThumbUp,
                                        contentDescription = "Hilfreiche Antwort",
                                        tint = if (feedback == true) themeColor else Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = { onFeedback(false) }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        Icons.Default.ThumbDown,
                                        contentDescription = "Problematische Antwort melden",
                                        tint = if (feedback == false) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                color = if (isUser) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(34.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF3D3D5C).copy(alpha = 0.8f), Color(0xFF2A2A45).copy(alpha = 0.6f))))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun UploadedImageCard(imageUrl: String, caption: String, _themeColor: Color) {
    Column {
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp)
                .clip(RoundedCornerShape(16.dp)).background(SurfaceDarkCard.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl, contentDescription = caption,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }
        if (caption.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(caption, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis())),
            color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun GeneratedImageCard(imageUrl: String, prompt: String, themeColor: Color) {
    var imageLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column {
        Box(
            modifier = Modifier.fillMaxWidth().height(280.dp)
                .clip(RoundedCornerShape(16.dp)).background(SurfaceDarkCard.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (!imageLoaded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = themeColor, strokeWidth = 3.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Generiere Bild...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            AsyncImage(
                model = imageUrl, contentDescription = prompt,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))) },
                onSuccess = { imageLoaded = true }, onError = { imageLoaded = true }
            )
        }
        if (prompt.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Prompt: $prompt", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Öffnen", tint = themeColor, modifier = Modifier.size(16.dp))
            }
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis())),
                color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun BubbleSourcesSection(
    sources: List<com.example.bamachat.data.model.ChatSource>,
    fetchedAtIso: String?,
    themeColor: Color
) {
    val context = LocalContext.current
    val fetchedLabel = fetchedAtIso?.takeIf { it.isNotBlank() }?.let { "Stand: $it" }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Live-Quellen", color = themeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (fetchedLabel != null) Text(fetchedLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        sources.take(4).forEachIndexed { index, source ->
            Surface(
                shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.04f),
                modifier = Modifier.fillMaxWidth().clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Public, null, tint = themeColor, modifier = Modifier.size(12.dp))
                        Text("${index + 1}. ${source.title}", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (source.snippet.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(source.snippet, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
