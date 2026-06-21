package com.example.bamachat.ui.component

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.BuildConfig
import com.example.bamachat.data.local.ConversationEntity
import com.example.bamachat.ui.theme.AppDesignPalette
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatDrawer(
    conversations: List<ConversationEntity>,
    currentId: String?,
    themeColor: Color,
    palette: AppDesignPalette,
    glassEffectsEnabled: Boolean,
    cornerRoundnessScale: Float,
    shadowIntensityScale: Float,
    surfaceOpacity: Float,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    val drawerCorner = (24f * cornerRoundnessScale).coerceIn(14f, 34f).dp
    val drawerShadow = (18f * shadowIntensityScale).coerceIn(4f, 34f).dp
    val drawerAlphaTop = if (glassEffectsEnabled) 0.96f else 1f
    val drawerAlphaMid = if (glassEffectsEnabled) 0.92f else 1f
    val drawerAlphaBottom = if (glassEffectsEnabled) 0.95f else 1f

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .shadow(drawerShadow, RoundedCornerShape(topEnd = drawerCorner, bottomEnd = drawerCorner))
            .clip(RoundedCornerShape(topEnd = drawerCorner, bottomEnd = drawerCorner))
            .border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (glassEffectsEnabled) 0.12f else 0.07f)),
                shape = RoundedCornerShape(topEnd = drawerCorner, bottomEnd = drawerCorner)
            ),
        drawerContainerColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.chatBgTop.copy(alpha = 0.96f),
                            palette.chatBgTop.copy(alpha = drawerAlphaTop * surfaceOpacity),
                            palette.chatBgMid.copy(alpha = drawerAlphaMid * surfaceOpacity),
                            palette.chatBgBottom.copy(alpha = drawerAlphaBottom * surfaceOpacity)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                palette.chatHeaderStart.copy(alpha = 0.98f),
                                palette.chatHeaderMid.copy(alpha = 0.95f),
                                palette.chatHeaderEnd.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .statusBarsPadding()
            ) {
                Column {
                    Text("BamaChat", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Deine Chats", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accentStrong),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 3.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Neuer Chat", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    ConversationRow(
                        conv = conv,
                        isActive = conv.id == currentId,
                        themeColor = themeColor,
                        palette = palette,
                        glassEffectsEnabled = glassEffectsEnabled,
                        cornerRoundnessScale = cornerRoundnessScale,
                        shadowIntensityScale = shadowIntensityScale,
                        surfaceOpacity = surfaceOpacity,
                        onClick = { onSelect(conv.id) },
                        onRename = {
                            renamingId = conv.id
                            renameText = conv.title
                        },
                        onDelete = { onDelete(conv.id) }
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "BamaChat · Version ${BuildConfig.VERSION_NAME}",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.sp
                )
            }
        }
    }
    renamingId?.let { id ->
        AlertDialog(
            onDismissRequest = { renamingId = null },
            title = { Text("Chat umbenennen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(id, renameText)
                    renamingId = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { renamingId = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
fun ConversationRow(
    conv: ConversationEntity,
    isActive: Boolean,
    themeColor: Color,
    palette: AppDesignPalette,
    glassEffectsEnabled: Boolean,
    cornerRoundnessScale: Float,
    shadowIntensityScale: Float,
    surfaceOpacity: Float,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val rowCorner = (14f * cornerRoundnessScale).coerceIn(10f, 24f).dp
    val rowShadowBase = if (isActive) 10f else 2f
    val rowShadow = (rowShadowBase * shadowIntensityScale).coerceIn(1.2f, 20f).dp
    val inactiveAlpha = if (glassEffectsEnabled) 0.05f else 0.08f
    val activeAlpha = (if (glassEffectsEnabled) 0.72f else 0.88f) * surfaceOpacity

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(rowShadow, RoundedCornerShape(rowCorner))
            .clip(RoundedCornerShape(rowCorner))
            .clickable { onClick() },
        color = if (isActive) {
            palette.chatAssistantSurface.copy(alpha = activeAlpha.coerceIn(0.55f, 1f))
        } else {
            Color.White.copy(alpha = inactiveAlpha)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) themeColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(rowCorner)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat, null,
                tint = if (isActive) themeColor else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conv.title, color = Color.White, fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date(conv.updatedAt)),
                    color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { onRename(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { onDelete(); menuExpanded = false }
                    )
                }
            }
        }
    }
}
