package com.example.bamachat.ui.component

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.voice.VoiceRuntimePresentation
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPink
import com.example.bamachat.ui.theme.SurfaceDarkElevated
import com.example.bamachat.ui.theme.SurfaceDarkInput
import com.example.bamachat.ui.viewmodel.ChatViewModel
import com.example.bamachat.voice.VoiceMode
import com.example.bamachat.voice.VoiceSessionState
import com.example.bamachat.voice.VoiceSessionUiState

data class PromptTemplate(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val description: String,
    val prompt: String
)

val defaultPromptTemplates = listOf<PromptTemplate>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Boolean,
    onImageGen: () -> Unit,
    onUpload: () -> Unit,
    onTakePhoto: () -> Unit,
    selectedImageUri: Uri?,
    onClearImage: () -> Unit,
    isListening: Boolean,
    voiceUiState: VoiceSessionUiState,
    voicePushToTalkEnabled: Boolean,
    onMicClick: () -> Unit,
    onMicPressStart: () -> Unit,
    onMicPressEnd: () -> Unit,
    onVoicePanelClick: () -> Unit,
    onStopVoice: () -> Unit,
    themeColor: Color,
    surfaceColor: Color,
    isLoading: Boolean,
    onStopGeneration: () -> Unit,
    designTokens: ChatDesignTokens,
    connectChatBottomBars: Boolean,
    glassEffectsEnabled: Boolean,
    uiCornerRoundnessScale: Float,
    uiShadowIntensityScale: Float,
    uiSurfaceOpacity: Float,
    automationQuickActionsEnabled: Boolean,
    selectedExtensionQuickAction: ChatViewModel.ExtensionQuickAction,
    onSelectExtensionQuickAction: (ChatViewModel.ExtensionQuickAction) -> Unit,
    promptTemplates: List<PromptTemplate> = emptyList(),
    compactMode: Boolean = false,
    onCompactBottomNavVisibilityChange: (Boolean) -> Unit = {},
    onSelectPromptTemplate: (PromptTemplate) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var moreActionsExpanded by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }

    val inputShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (connectChatBottomBars) 0.dp else 20.dp,
        bottomEnd = if (connectChatBottomBars) 0.dp else 20.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .imePadding()
            .animateContentSize(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing))
    ) {
        // Selected image preview
        AnimatedVisibility(
            visible = selectedImageUri != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            selectedImageUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceDarkElevated,
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Vorschau",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            IconButton(
                                onClick = onClearImage,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Entfernen",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main Input Container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = (16f * uiShadowIntensityScale).coerceIn(4f, 32f).dp,
                    shape = inputShape,
                    spotColor = NeonPurple.copy(alpha = 0.15f),
                    ambientColor = NeonPurple.copy(alpha = 0.08f)
                ),
            shape = inputShape,
            color = SurfaceDarkElevated.copy(alpha = (0.92f * uiSurfaceOpacity).coerceIn(0.55f, 1f)),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF252545).copy(alpha = 0.8f),
                                Color(0xFF1E1E3A).copy(alpha = 0.9f)
                            )
                        ),
                        shape = inputShape
                    )
                    .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
            ) {
                if (automationQuickActionsEnabled) {
                    val quickActions = listOf(
                        ChatViewModel.ExtensionQuickAction.AUTO to "Auto",
                        ChatViewModel.ExtensionQuickAction.RESEARCH to "Research",
                        ChatViewModel.ExtensionQuickAction.CODE_REVIEW to "Code",
                        ChatViewModel.ExtensionQuickAction.PLAN to "Plan"
                    )
                    val selectedLabel = quickActions.firstOrNull { it.first == selectedExtensionQuickAction }?.second ?: "Auto"
                    Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        AssistChip(
                            onClick = { modeMenuExpanded = true },
                            label = { Text("Modus: $selectedLabel") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) }
                        )
                        DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                            quickActions.forEach { (action, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        if (action == selectedExtensionQuickAction) Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        onSelectExtensionQuickAction(action)
                                        modeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = voiceUiState.state !is VoiceSessionState.Idle,
                    enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.96f),
                    exit = fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.96f)
                ) {
                    VoiceSessionStatus(
                        uiState = voiceUiState,
                        onOpenPanel = onVoicePanelClick,
                        onStopVoice = onStopVoice
                    )
                }

                // Text input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Plus / Attach button
                    InputActionButton(
                        icon = Icons.Default.Add,
                        contentDesc = "Mehr Aktionen",
                        tint = NeonCyan,
                        onClick = { moreActionsExpanded = true }
                    )

                    // Text field
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { onInputChange(it) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 120.dp),
                        placeholder = {
                            Text(
                                "Nachricht eingeben...",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 14.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 14.sp
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDarkInput,
                            unfocusedContainerColor = SurfaceDarkInput,
                            focusedBorderColor = NeonPurple.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                            cursorColor = NeonPurple,
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.35f)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                focusManager.clearFocus()
                                onSend(inputText)
                            }
                        ),
                        maxLines = 4,
                        singleLine = false
                    )

                    // Mic button
                    VoiceMicButton(
                        isListening = isListening,
                        isPreparing = voiceUiState.state is VoiceSessionState.Preparing,
                        pushToTalkEnabled = voicePushToTalkEnabled,
                        onClick = onMicClick,
                        onPressStart = onMicPressStart,
                        onPressEnd = onMicPressEnd
                    )
                }

                // Bottom action row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: AI status indicator
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onVoicePanelClick)
                            .semantics {
                                contentDescription = "BamaVoice öffnen"
                                role = Role.Button
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isLoading) NeonPink.copy(alpha = 0.8f)
                                    else NeonCyan.copy(alpha = 0.6f)
                                )
                        )
                        Text(
                            text = when {
                                voiceUiState.state is VoiceSessionState.Speaking -> "BamaVoice spricht"
                                isLoading -> "KI antwortet..."
                                else -> "BamaVoice"
                            },
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Right: Stop / Send
                    if (isLoading) {
                        // Stop button
                        FilledIconButton(
                            onClick = onStopGeneration,
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    spotColor = NeonPink.copy(alpha = 0.4f)
                                ),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = NeonPink,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                "Stop",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // Send button
                        FilledIconButton(
                            onClick = {
                                focusManager.clearFocus()
                                onSend(inputText)
                            },
                            enabled = inputText.isNotBlank() || selectedImageUri != null,
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    spotColor = if (inputText.isNotBlank() || selectedImageUri != null)
                                        NeonPurple.copy(alpha = 0.4f)
                                    else Color.Transparent
                                ),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (inputText.isNotBlank() || selectedImageUri != null)
                                    NeonPurple
                                else Color(0xFF2A2A45),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF2A2A45),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Senden",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dropdown for more actions
        DropdownMenu(
            expanded = moreActionsExpanded,
            onDismissRequest = { moreActionsExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Bild hochladen", fontSize = 13.sp) },
                onClick = {
                    moreActionsExpanded = false
                    onUpload()
                },
                leadingIcon = {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                }
            )
            DropdownMenuItem(
                text = { Text("Foto aufnehmen", fontSize = 13.sp) },
                onClick = {
                    moreActionsExpanded = false
                    onTakePhoto()
                },
                leadingIcon = {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                }
            )
            DropdownMenuItem(
                text = { Text("Bild generieren", fontSize = 13.sp) },
                onClick = {
                    moreActionsExpanded = false
                    onImageGen()
                },
                leadingIcon = {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                }
            )
        }
    }
}

@Composable
private fun VoiceSessionStatus(
    uiState: VoiceSessionUiState,
    onOpenPanel: () -> Unit,
    onStopVoice: () -> Unit
) {
    val state = uiState.state
    val runtimeStatusText = if (uiState.mode == VoiceMode.LIVE) {
        VoiceRuntimePresentation.resolve().statusText(state)
    } else {
        null
    }
    val statusText = runtimeStatusText ?: when (state) {
        VoiceSessionState.Idle -> ""
        VoiceSessionState.Preparing -> "Mikrofon wird vorbereitet …"
        VoiceSessionState.Connecting -> "Sichere Verbindung wird hergestellt …"
        is VoiceSessionState.Reconnecting -> "Verbindung wird wiederhergestellt …"
        VoiceSessionState.Listening -> "Ich höre zu …"
        is VoiceSessionState.Transcribing -> state.partialText.ifBlank { "Ich höre zu …" }
        VoiceSessionState.Thinking -> "BamaFlow denkt …"
        VoiceSessionState.Speaking -> "BamaFlow spricht …"
        VoiceSessionState.Interrupted -> "Sprachausgabe unterbrochen"
        VoiceSessionState.Ended -> "Live-Unterhaltung beendet"
        is VoiceSessionState.Error -> state.userMessage
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .heightIn(min = 48.dp)
            .clickable(onClick = onOpenPanel),
        shape = RoundedCornerShape(14.dp),
        color = NeonPurple.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state) {
                VoiceSessionState.Preparing,
                VoiceSessionState.Connecting,
                is VoiceSessionState.Reconnecting -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = NeonCyan
                )
                VoiceSessionState.Listening,
                is VoiceSessionState.Transcribing -> VoiceInputLevel(uiState.inputLevel)
                VoiceSessionState.Speaking -> VoiceVisualizer(NeonPurple)
                VoiceSessionState.Thinking -> Icon(Icons.Default.AutoAwesome, null, tint = NeonCyan)
                VoiceSessionState.Interrupted -> Icon(Icons.Default.PauseCircle, null, tint = NeonPink)
                VoiceSessionState.Ended -> Icon(Icons.Default.CallEnd, null, tint = NeonCyan)
                is VoiceSessionState.Error -> Icon(Icons.Default.ErrorOutline, null, tint = NeonPink)
                VoiceSessionState.Idle -> Unit
            }
            Text(
                text = statusText,
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (state == VoiceSessionState.Speaking || state == VoiceSessionState.Thinking) {
                IconButton(
                    onClick = onStopVoice,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.StopCircle,
                        contentDescription = "Sprachausgabe stoppen",
                        tint = NeonPink
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceInputLevel(level: Float) {
    Row(
        modifier = Modifier.width(26.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val amplitude = (4.dp * (index + 1)) * level.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(5.dp + amplitude)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (level > 0.05f) NeonPink else NeonCyan.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
private fun VoiceMicButton(
    isListening: Boolean,
    isPreparing: Boolean,
    pushToTalkEnabled: Boolean,
    onClick: () -> Unit,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val contentDesc = if (isListening || isPreparing) {
        "Spracheingabe beenden"
    } else {
        "Spracheingabe starten"
    }
    val tint = if (isListening || isPreparing) NeonPink else NeonCyan
    var focused by remember { mutableStateOf(false) }
    val interactionModifier = if (pushToTalkEnabled) {
        Modifier.pointerInput(onPressStart, onPressEnd) {
            detectTapGestures(
                onPress = {
                    onPressStart()
                    try {
                        tryAwaitRelease()
                    } finally {
                        onPressEnd()
                    }
                }
            )
        }
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(interactionModifier)
            .focusable()
            .clip(CircleShape)
            .background(tint.copy(alpha = if (isListening || isPreparing) 0.25f else 0.1f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else tint.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .semantics {
                contentDescription = contentDesc
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isPreparing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = tint
            )
        } else {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun InputActionButton(
    icon: ImageVector,
    contentDesc: String,
    tint: Color,
    containerAlpha: Float = 0.1f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = containerAlpha))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = tint.copy(alpha = 0.9f),
            modifier = Modifier.size(22.dp)
        )
    }
}
