package com.example.bamachat.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPink
import com.example.bamachat.ui.theme.NeonGreen
import com.example.bamachat.ui.theme.SurfaceDarkCard
import com.example.bamachat.ui.theme.SurfaceDarkElevated
import com.example.bamachat.ui.theme.TextSecondary
import com.example.bamachat.util.ProjectWorkspace
import com.example.bamachat.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenChat: (workspaceId: String) -> Unit = {}
) {
    val projectWorkspaces by settingsViewModel.projectWorkspaces.collectAsStateWithLifecycle()
    val activeWorkspaceId by settingsViewModel.activeWorkspaceId.collectAsStateWithLifecycle()
    val workspaceChatFilterEnabled by settingsViewModel.workspaceChatFilterEnabled.collectAsStateWithLifecycle()

    var newName by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renamingDraft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProjectWorkspace?>(null) }
    val focusManager = LocalFocusManager.current

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Workspace löschen") },
            text = { Text("\"${deleteTarget?.name}\" endgültig löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget?.let { settingsViewModel.deleteWorkspace(it.id) }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink)
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Workspaces", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    )
                ),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // New workspace input
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDarkCard.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Neuen Workspace erstellen", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("z.B. Kundenprojekt Alpha", fontSize = 14.sp) },
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    val name = newName.trim()
                                    if (name.isNotBlank() && settingsViewModel.createWorkspace(name)) {
                                        newName = ""
                                        focusManager.clearFocus()
                                    }
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceDarkElevated,
                                    unfocusedContainerColor = SurfaceDarkElevated,
                                    focusedBorderColor = NeonCyan.copy(alpha = 0.4f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                    cursorColor = NeonCyan
                                )
                            )
                            FilledTonalButton(
                                onClick = {
                                    val name = newName.trim()
                                    if (name.isNotBlank() && settingsViewModel.createWorkspace(name)) {
                                        newName = ""
                                        focusManager.clearFocus()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonCyan.copy(alpha = 0.2f))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Erstellen", tint = NeonCyan)
                            }
                        }
                    }
                }
            }

            item {
                // Filter toggle
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceDarkCard.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            Text("Chat-Filter: Nur aktiven Workspace anzeigen", color = TextSecondary, fontSize = 13.sp)
                        }
                        Switch(
                            checked = workspaceChatFilterEnabled,
                            onCheckedChange = { settingsViewModel.setWorkspaceChatFilterEnabled(it) }
                        )
                    }
                }
            }

            items(projectWorkspaces, key = { it.id }) { workspace ->
                WorkspaceCard(
                    workspace = workspace,
                    isActive = workspace.id == activeWorkspaceId,
                    isRenaming = renamingId == workspace.id,
                    renamingDraft = renamingDraft,
                    onRenamingDraftChange = { renamingDraft = it },
                    onStartRename = {
                        renamingId = workspace.id
                        renamingDraft = workspace.name
                    },
                    onConfirmRename = {
                        if (settingsViewModel.renameWorkspace(workspace.id, renamingDraft)) {
                            renamingId = null
                            renamingDraft = ""
                            focusManager.clearFocus()
                        }
                    },
                    onCancelRename = {
                        renamingId = null
                        renamingDraft = ""
                        focusManager.clearFocus()
                    },
                    onActivate = { settingsViewModel.setActiveWorkspace(workspace.id) },
                    onDelete = { deleteTarget = workspace },
                    onOpenChat = { onOpenChat(workspace.id) }
                )
            }
            item { Spacer(Modifier.height(110.dp)) }
        }
    }
}

@Composable
private fun WorkspaceCard(
    workspace: ProjectWorkspace,
    isActive: Boolean,
    isRenaming: Boolean,
    renamingDraft: String,
    onRenamingDraftChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onOpenChat: () -> Unit
) {
    val accent = if (isActive) NeonGreen else NeonPurple

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .shadow(
                elevation = if (isActive) 8.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = accent.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) SurfaceDarkElevated else SurfaceDarkCard.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isActive) accent.copy(alpha = 0.4f) else accent.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRenaming) {
                    OutlinedTextField(
                        value = renamingDraft,
                        onValueChange = onRenamingDraftChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Name") },
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onConfirmRename() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDarkElevated,
                            unfocusedContainerColor = SurfaceDarkElevated,
                            focusedBorderColor = accent.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                            cursorColor = accent
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onConfirmRename) {
                        Icon(Icons.Default.Check, contentDescription = "Speichern", tint = NeonGreen)
                    }
                    IconButton(onClick = onCancelRename) {
                        Icon(Icons.Default.Close, contentDescription = "Abbrechen", tint = TextSecondary)
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(10.dp).clip(CircleShape).background(accent.copy(alpha = if (isActive) 1f else 0.4f))
                            )
                            Text(workspace.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        if (workspace.description.isNotBlank()) {
                            Text(workspace.description, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isActive) {
                        Surface(shape = RoundedCornerShape(999.dp), color = NeonGreen.copy(alpha = 0.15f)) {
                            Text("Aktiv", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chat button
                FilledTonalButton(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Chats", fontSize = 12.sp, color = accent)
                }

                // Activate button (if not active)
                if (!isActive) {
                    OutlinedButton(
                        onClick = onActivate,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
                    ) {
                        Text("Aktivieren", fontSize = 12.sp, color = NeonCyan)
                    }
                }

                // Rename button
                IconButton(onClick = onStartRename, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Umbenennen", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }

                // Delete button (not for default)
                if (workspace.id != "ws-default") {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = NeonPink.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
