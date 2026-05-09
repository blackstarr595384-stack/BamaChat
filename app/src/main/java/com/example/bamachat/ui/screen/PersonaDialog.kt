package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.viewmodel.ChatViewModel

@Composable
fun PersonaDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val selected by viewModel.personaViewModel.selectedPersona.collectAsStateWithLifecycle()
    var promptText by remember { mutableStateOf(viewModel.personaViewModel.getEditablePromptForPersona(selected)) }

    LaunchedEffect(selected) {
        promptText = viewModel.personaViewModel.getEditablePromptForPersona(selected)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Persona wählen", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                ChatViewModel.Persona.entries.forEach { persona ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.personaViewModel.setSelectedPersona(persona)
                            },
                        color = if (selected == persona) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(persona.emoji, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(persona.displayName, fontWeight = FontWeight.SemiBold)
                                val preview = viewModel.personaViewModel.getEditablePromptForPersona(persona)
                                Text(
                                    preview.take(75) + if (preview.length > 75) "..." else "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 2
                                )
                            }
                            if (selected == persona) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("Prompt für: " + selected.displayName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp),
                    minLines = 4,
                    maxLines = 10,
                    placeholder = { Text("Persona-Prompt bearbeiten...") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.personaViewModel.setPromptForPersona(selected, promptText)
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.personaViewModel.resetPromptForPersona(selected)
            }) { Text("Zurücksetzen") }
        }
    )
}
