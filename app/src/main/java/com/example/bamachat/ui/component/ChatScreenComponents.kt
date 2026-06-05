package com.example.bamachat.ui.component

import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipboardManager as ComposeClipboardManager
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bamachat.data.model.ChatMessage
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast

@Composable
fun ChatBubble(
    message: ChatMessage,
    onSpeak: (String) -> Unit,
    themeColor: Color,
    surfaceColor: Color,
    fontSize: Float,
    showTimestamps: Boolean = true,
    showLiveSources: Boolean = true,
    animateIn: Boolean = true,
    animationDelayMs: Int = 0,
    designPreset: ChatDesignPreset,
    designTokens: ChatDesignTokens
) {
    val isUser = message.isUser
    val composeClipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showMessageActions by remember(message.id) { mutableStateOf(false) }
    var sourcesExpandedByUser by remember(message.id) { mutableStateOf(false) }
    var visible by remember(message.id) { mutableStateOf(!animateIn) }
    val hasLiveSources = message.sources.isNotEmpty()
    val showSourcesSection = hasLiveSources && (showLiveSources || sourcesExpandedByUser)
    LaunchedEffect(message.id, animateIn) {
        if (animateIn) kotlinx.coroutines.delay(animationDelayMs.toLong())
        visible = true
    }
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f, animationSpec = tween(320), label = "bubbleAlpha"
    )
    val bubbleShift by animateDpAsState(
        targetValue = if (visible) 0.dp else 10.dp, animationSpec = tween(320), label = "bubbleShift"
    )
    val bubbleShape = if (isUser) RoundedCornerShape(
        designTokens.userBubbleRoundness, 4.dp, designTokens.userBubbleRoundness, designTokens.userBubbleRoundness
    ) else RoundedCornerShape(
        4.dp, designTokens.assistantBubbleRoundness, designTokens.assistantBubbleRoundness, designTokens.assistantBubbleRoundness
    )
    val bubbleBrush = if (isUser) {
        when (designPreset) {
            ChatDesignPreset.GLASS -> Brush.horizontalGradient(listOf(Color(0xFF4F8CFF), Color(0xFF7F7FD5), Color(0xFF43C6AC)))
            ChatDesignPreset.EDITORIAL -> Brush.horizontalGradient(listOf(Color(0xFFB35134), Color(0xFF8E3D30)))
            ChatDesignPreset.NOIR -> Brush.horizontalGradient(listOf(Color(0xFF315A9F), Color(0xFF5B7CC0)))
            ChatDesignPreset.SOLAR -> Brush.horizontalGradient(listOf(Color(0xFFE07A2F), Color(0xFFF4AE62)))
            ChatDesignPreset.DASHBOARD -> Brush.horizontalGradient(listOf(Color(0xFF0E7490), Color(0xFF2563EB)))
            ChatDesignPreset.CURRENT -> Brush.horizontalGradient(listOf(themeColor, themeColor.copy(alpha = 0.75f)))
        }
    } else {
        when (designPreset) {
            ChatDesignPreset.GLASS -> Brush.verticalGradient(listOf(surfaceColor.copy(alpha = 0.72f), surfaceColor.copy(alpha = 0.56f)))
            ChatDesignPreset.EDITORIAL -> Brush.verticalGradient(listOf(surfaceColor.copy(alpha = 0.98f), surfaceColor.copy(alpha = 0.92f)))
            ChatDesignPreset.NOIR -> Brush.verticalGradient(listOf(surfaceColor.copy(alpha = 0.84f), surfaceColor.copy(alpha = 0.72f)))
            ChatDesignPreset.SOLAR -> Brush.verticalGradient(listOf(surfaceColor.copy(alpha = 0.92f), surfaceColor.copy(alpha = 0.8f)))
            ChatDesignPreset.DASHBOARD -> Brush.verticalGradient(listOf(surfaceColor.copy(alpha = 0.9f), surfaceColor.copy(alpha = 0.82f)))
            ChatDesignPreset.CURRENT -> Brush.verticalGradient(listOf(surfaceColor.copy(alpha = 0.98f), surfaceColor.copy(alpha = 0.8f)))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(x = 0, y = bubbleShift.roundToPx()) },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(
                    when (designPreset) {
                        ChatDesignPreset.GLASS -> Brush.radialGradient(listOf(Color(0xFF8BB7FF), Color(0xFF4F8CFF)))
                        ChatDesignPreset.EDITORIAL -> Brush.radialGradient(listOf(Color(0xFFD17A52), Color(0xFF8E3D30)))
                        ChatDesignPreset.NOIR -> Brush.radialGradient(listOf(Color(0xFF7292D6), Color(0xFF315A9F)))
                        ChatDesignPreset.SOLAR -> Brush.radialGradient(listOf(Color(0xFFFFC387), Color(0xFFE07A2F)))
                        ChatDesignPreset.DASHBOARD -> Brush.radialGradient(listOf(Color(0xFF22D3EE), Color(0xFF0E7490)))
                        ChatDesignPreset.CURRENT -> Brush.radialGradient(listOf(themeColor, themeColor.copy(alpha = 0.5f)))
                    }
                ),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            modifier = Modifier.widthIn(max = designTokens.bubbleMaxWidth)
                .shadow(designTokens.bubbleShadow, bubbleShape, spotColor = if (isUser) themeColor else surfaceColor.copy(alpha = 0.6f)),
            shape = bubbleShape, color = Color.Transparent
        ) {
            Box(modifier = Modifier.background(bubbleBrush).padding(14.dp).graphicsLayer(alpha = bubbleAlpha)) {
                Column {
                    if (message.imageUrl != null) {
                        if (isUser) UploadedImageCard(message.imageUrl, message.text, themeColor)
                        else GeneratedImageCard(message.imageUrl, message.text, themeColor)
                    } else if (isUser) {
                        SelectionContainer {
                            Text(
                                message.text,
                                color = Color.White,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.35f).sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        if (message.text.isBlank()) BlinkingDot(themeColor)
                        else {
                            MarkdownText(
                                markdown = message.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFEDEEF0),
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.5f).sp
                                ),
                                isTextSelectable = true
                            )
                            if (showSourcesSection) {
                                Spacer(Modifier.height(10.dp))
                                BubbleSourcesSection(message.sources, message.webFetchedAtIso, themeColor)
                            }
                            if (hasLiveSources && !showLiveSources) {
                                Spacer(Modifier.height(6.dp))
                                AssistChip(
                                    onClick = { sourcesExpandedByUser = !sourcesExpandedByUser },
                                    label = {
                                        Text(
                                            text = if (sourcesExpandedByUser) "Quellen" else "Quellen (${message.sources.size})",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Public,
                                            contentDescription = null,
                                            tint = themeColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            if (sourcesExpandedByUser) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.72f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.08f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color.White.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val copied = copyMessageText(context, composeClipboard, message.text)
                                        Toast.makeText(
                                            context,
                                            if (copied) "Nachricht kopiert" else "Kopieren fehlgeschlagen",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Kopieren", tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onSpeak(message.text) }, modifier = Modifier.size(28.dp)) {
                                    @Suppress("DEPRECATION")
                                    Icon(Icons.Default.VolumeUp, "Vorlesen", tint = themeColor, modifier = Modifier.size(16.dp))
                                }
                                Box {
                                    IconButton(onClick = { showMessageActions = true }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.MoreVert, "Mehr", tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showMessageActions,
                                        onDismissRequest = { showMessageActions = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Nachricht kopieren") },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                            onClick = {
                                                showMessageActions = false
                                                val copied = copyMessageText(context, composeClipboard, message.text)
                                                Toast.makeText(
                                                    context,
                                                    if (copied) "Nachricht kopiert" else "Kopieren fehlgeschlagen",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Vorlesen") },
                                            leadingIcon = {
                                                @Suppress("DEPRECATION")
                                                Icon(Icons.Default.VolumeUp, contentDescription = null)
                                            },
                                            onClick = {
                                                showMessageActions = false
                                                onSpeak(message.text)
                                            }
                                        )
                                    }
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
                contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
fun UploadedImageCard(imageUrl: String, caption: String, _themeColor: Color) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E2024)),
            contentAlignment = Alignment.Center) {
            AsyncImage(model = imageUrl, contentDescription = caption,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        }
        if (caption.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(caption, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.height(4.dp))
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis())),
            color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
    }
}

@Composable
fun GeneratedImageCard(imageUrl: String, prompt: String, themeColor: Color) {
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
        if (prompt.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("Prompt: $prompt", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Öffnen", tint = themeColor, modifier = Modifier.size(16.dp))
            }
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis())),
                color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Live-Quellen", color = themeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (fetchedLabel != null) Text(fetchedLabel, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
        sources.take(4).forEachIndexed { index, source ->
            Surface(
                shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Public, null, tint = themeColor, modifier = Modifier.size(12.dp))
                        Text("${index + 1}. ${source.title}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (source.snippet.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(source.snippet, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun copyMessageText(
    context: Context,
    composeClipboard: ComposeClipboardManager,
    text: String
): Boolean {
    if (text.isBlank()) return false
    val clipboardCopied = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) return@runCatching false
        clipboard.setPrimaryClip(ClipData.newPlainText("BamaChat Nachricht", text))
        val clip = clipboard.primaryClip
        val copiedText = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        copiedText.isNotBlank()
    }.getOrDefault(false)

    val composeCopied = runCatching {
        composeClipboard.setText(AnnotatedString(text))
        true
    }.getOrDefault(false)

    return clipboardCopied || composeCopied
}
