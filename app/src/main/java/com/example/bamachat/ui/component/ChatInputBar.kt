package com.example.bamachat.ui.component

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bamachat.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.withTimeoutOrNull

data class PromptTemplate(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val prompt: String
)

val defaultPromptTemplates = listOf(
    PromptTemplate("translate", "Übersetzen", Icons.Default.Translate, "Text übersetzen", "Übersetze den folgenden Text ins Deutsche:\n\n"),
    PromptTemplate("summarize", "Zusammenfassen", Icons.Default.Summarize, "Text zusammenfassen", "Fasse den folgenden Text präzise zusammen:\n\n"),
    PromptTemplate("explain", "Erklären", Icons.Default.Lightbulb, "Komplexes erklären", "Erkläre das folgende Konzept einfach und verständlich:\n\n"),
    PromptTemplate("grammar", "Grammatik", Icons.Default.Spellcheck, "Grammatik korrigieren", "Korrigiere die Grammatik und Rechtschreibung in:\n\n"),
    PromptTemplate("code_review", "Code Review", Icons.Default.Code, "Code überprüfen", "Führe einen Code Review durch. Analysiere:\n- Sicherheitslücken\n- Performance\n- Best Practices\n\nCode:\n"),
    PromptTemplate("brainstorm", "Brainstorming", Icons.Default.Cloud, "Ideen sammeln", "Brainstorme zum Thema:\n\n"),
    PromptTemplate("outline", "Gliederung", Icons.AutoMirrored.Filled.ListAlt, "Gliederung erstellen", "Erstelle eine detaillierte Gliederung für:\n\n"),
    PromptTemplate("email", "E-Mail", Icons.Default.Email, "E-Mail schreiben", "Schreibe eine professionelle E-Mail zum Thema:\n\n"),
)

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
    voicePushToTalkEnabled: Boolean,
    onMicClick: () -> Unit,
    onMicPressStart: () -> Unit,
    onMicPressEnd: () -> Unit,
    themeColor: Color,
    surfaceColor: Color,
    isLoading: Boolean,
    designTokens: ChatDesignTokens,
    connectChatBottomBars: Boolean,
    glassEffectsEnabled: Boolean,
    uiCornerRoundnessScale: Float,
    uiShadowIntensityScale: Float,
    uiSurfaceOpacity: Float,
    automationQuickActionsEnabled: Boolean,
    selectedExtensionQuickAction: ChatViewModel.ExtensionQuickAction,
    onSelectExtensionQuickAction: (ChatViewModel.ExtensionQuickAction) -> Unit,
    compactMode: Boolean = false,
    onCompactBottomNavVisibilityChange: (Boolean) -> Unit = {},
    promptTemplates: List<PromptTemplate> = emptyList(),
    onSelectPromptTemplate: (PromptTemplate) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var compactSheetExpanded by remember(compactMode) { mutableStateOf(false) }
    val showExpandedComposer = !compactMode || compactSheetExpanded
    val topRadius = (24f * uiCornerRoundnessScale).coerceIn(14f, 34f).dp
    val bottomRadius = (if (connectChatBottomBars) 0f else 14f * uiCornerRoundnessScale).coerceAtLeast(0f).dp
    val horizontalInset = if (connectChatBottomBars) 0.dp else 10.dp
    val baseAlpha = if (glassEffectsEnabled) 0.74f else 0.9f
    val containerAlpha = if (connectChatBottomBars) {
        0.96f
    } else {
        (baseAlpha * uiSurfaceOpacity).coerceIn(0.55f, 1f)
    }
    val shadowElevation = if (connectChatBottomBars) {
        (6f * uiShadowIntensityScale).coerceIn(0f, 12f).dp
    } else {
        (22f * uiShadowIntensityScale).coerceIn(4f, 36f).dp
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
            .imePadding()
            .animateContentSize(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)),
        color = surfaceColor.copy(alpha = containerAlpha),
        shadowElevation = shadowElevation,
        shape = RoundedCornerShape(topStart = topRadius, topEnd = topRadius, bottomStart = bottomRadius, bottomEnd = bottomRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (glassEffectsEnabled) 0.14f else 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            var localInputValue by remember { mutableStateOf(TextFieldValue(inputText)) }
            var sendLatchUntil by remember { mutableLongStateOf(0L) }
            LaunchedEffect(inputText) {
                if (inputText != localInputValue.text) {
                    localInputValue = TextFieldValue(
                        text = inputText,
                        selection = TextRange(inputText.length)
                    )
                }
            }
            if (showExpandedComposer && selectedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Vorschau",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(surfaceColor.copy(alpha = 0.9f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Bild angehängt",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Close, "Entfernen", tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            val quickActionOptions = remember {
                listOf(
                    ChatViewModel.ExtensionQuickAction.AUTO,
                    ChatViewModel.ExtensionQuickAction.RESEARCH,
                    ChatViewModel.ExtensionQuickAction.CODE_REVIEW,
                    ChatViewModel.ExtensionQuickAction.PLAN
                )
            }
            var moreActionsExpanded by remember { mutableStateOf(false) }
            var smartActionExpanded by remember { mutableStateOf(false) }
            val canSend = !isLoading && (localInputValue.text.trim().isNotEmpty() || selectedImageUri != null)
            val sendButtonColor by animateColorAsState(
                targetValue = if (canSend) themeColor else Color(0xFF35383D),
                animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing),
                label = "sendButtonColor"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (compactMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .padding(horizontal = 110.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .pointerInput(compactSheetExpanded) {
                                var totalDrag = 0f
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        totalDrag += dragAmount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        when {
                                            totalDrag < -18f -> {
                                                compactSheetExpanded = true
                                                onCompactBottomNavVisibilityChange(true)
                                            }
                                            totalDrag > 18f -> {
                                                compactSheetExpanded = false
                                                onCompactBottomNavVisibilityChange(false)
                                            }
                                        }
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f }
                                )
                            }
                            .clickable {
                                compactSheetExpanded = false
                                onCompactBottomNavVisibilityChange(false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                        )
                    }
                }
                val triggerSend: () -> Unit = send@{
                    val now = System.currentTimeMillis()
                    if (now < sendLatchUntil) return@send
                    val submitted = localInputValue.text
                    val hasInput = submitted.trim().isNotEmpty() || selectedImageUri != null
                    if (!isLoading && hasInput) {
                        val accepted = onSend(submitted)
                        if (accepted) {
                            sendLatchUntil = now + 220L
                            focusManager.clearFocus(force = true)
                            localInputValue = TextFieldValue("")
                            onInputChange("")
                        }
                    }
                }
                TextField(
                    value = localInputValue,
                    onValueChange = {
                        localInputValue = it
                        onInputChange(it.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 118.dp),
                    placeholder = { Text("Schreib was... ( / für Befehle)", color = Color.White.copy(alpha = 0.4f)) },
                    shape = RoundedCornerShape((designTokens.inputCornerRadius - 6.dp).coerceAtLeast(10.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = surfaceColor.copy(alpha = 0.95f),
                        unfocusedContainerColor = surfaceColor.copy(alpha = 0.9f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = themeColor
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { triggerSend() }),
                    minLines = 1,
                    maxLines = 3
                )
                // Slash command palette dropdown
                val showSlashMenu = localInputValue.text.startsWith("/") && !isLoading
                if (showSlashMenu) {
                    val filteredTemplates = promptTemplates.filter {
                        val query = localInputValue.text.removePrefix("/").trim().lowercase()
                        query.isBlank() || it.label.lowercase().contains(query) || it.id.contains(query)
                    }
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = {
                            localInputValue = TextFieldValue(localInputValue.text.removePrefix("/").trim())
                            onInputChange(localInputValue.text)
                        }
                    ) {
                        if (filteredTemplates.isEmpty()) {
                            Text(
                                text = "Keine Befehle gefunden",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        } else {
                            filteredTemplates.forEach { template ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(template.icon, null, tint = themeColor, modifier = Modifier.size(20.dp))
                                            Column {
                                                Text(template.label, fontSize = 15.sp)
                                                Text(template.description, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                                            }
                                        }
                                    },
                                    onClick = {
                                        localInputValue = TextFieldValue(template.prompt)
                                        onInputChange(template.prompt)
                                        onSelectPromptTemplate(template)
                                    }
                                )
                            }
                        }
                    }
                }
                if (showExpandedComposer) {
                    if (automationQuickActionsEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickActionOptions.forEach { action ->
                                FilterChip(
                                    selected = selectedExtensionQuickAction == action,
                                    onClick = { onSelectExtensionQuickAction(action) },
                                    label = {
                                        Text(
                                            text = action.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    },
                                    leadingIcon = if (selectedExtensionQuickAction == action) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { smartActionExpanded = true },
                                label = {
                                    Text(
                                        text = "Smart: ${selectedExtensionQuickAction.label}",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = smartActionExpanded,
                            onDismissRequest = { smartActionExpanded = false }
                        ) {
                            quickActionOptions.forEach { action ->
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (selectedExtensionQuickAction == action) {
                                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = themeColor)
                                            }
                                            Column {
                                                Text(action.label, fontSize = 15.sp)
                                                Text(
                                                    text = when (action) {
                                                        ChatViewModel.ExtensionQuickAction.AUTO -> "Automatisch anpassen"
                                                        ChatViewModel.ExtensionQuickAction.RESEARCH -> "Quellen & Evidenz"
                                                        ChatViewModel.ExtensionQuickAction.CODE_REVIEW -> "Bugs & Risiken"
                                                        ChatViewModel.ExtensionQuickAction.PLAN -> "Prioritäten & Schritte"
                                                    },
                                                    fontSize = 13.sp,
                                                    color = Color.White.copy(alpha = 0.65f)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        smartActionExpanded = false
                                        onSelectExtensionQuickAction(action)
                                    }
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Spracherkennung starten"
                            }
                            .clip(CircleShape)
                            .background(if (isListening) themeColor.copy(alpha = 0.25f) else surfaceColor.copy(alpha = 0.92f))
                            .then(
                                if (voicePushToTalkEnabled) {
                                    Modifier.pointerInput(isListening) {
                                        detectTapGestures(
                                            onPress = {
                                                // P1-5: require a short hold (150 ms) before starting
                                                // STT so accidental taps don't fire a tiny recognition
                                                // window that the user never intended.
                                                val held = withTimeoutOrNull(150L) {
                                                    tryAwaitRelease()
                                                }
                                                if (held == null) {
                                                    // user is still holding past the threshold → start STT
                                                    onMicPressStart()
                                                    tryAwaitRelease()
                                                    onMicPressEnd()
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier.clickable { onMicClick() }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isListening) VoiceVisualizer(themeColor)
                        else Icon(Icons.Default.Mic, "Diktieren", tint = Color.White.copy(alpha = 0.7f))
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Import und mehr Aktionen"
                            }
                            .clip(CircleShape)
                            .background(surfaceColor.copy(alpha = 0.92f))
                            .clickable { moreActionsExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "Mehr", tint = Color.White.copy(alpha = 0.7f))
                        DropdownMenu(
                            expanded = moreActionsExpanded,
                            onDismissRequest = { moreActionsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Bild hochladen", fontSize = 15.sp) },
                                onClick = {
                                    moreActionsExpanded = false
                                    onUpload()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Foto aufnehmen", fontSize = 15.sp) },
                                onClick = {
                                    moreActionsExpanded = false
                                    onTakePhoto()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bild generieren", fontSize = 15.sp) },
                                onClick = {
                                    moreActionsExpanded = false
                                    onImageGen()
                                }
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledIconButton(
                        onClick = { triggerSend() },
                        enabled = canSend,
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = if (canSend) 8.dp else 0.dp,
                                shape = CircleShape,
                                spotColor = themeColor.copy(alpha = 0.45f)
                            ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = sendButtonColor,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF35383D),
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, "Senden", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
