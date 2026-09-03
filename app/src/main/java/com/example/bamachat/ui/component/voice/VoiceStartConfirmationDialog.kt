package com.example.bamachat.ui.component.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.bamachat.ui.voice.VoiceRuntimePresentationModel

@Composable
internal fun VoiceStartConfirmationDialog(
    presentation: VoiceRuntimePresentationModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("voice_start_confirmation_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(presentation.startDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = presentation.startDialogIntro,
                    style = MaterialTheme.typography.bodyLarge
                )
                presentation.startDialogHighlights.forEach { highlight ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("•")
                        Text(highlight, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("voice_start_confirm"),
                onClick = onConfirm
            ) {
                Text(presentation.startActionLabel)
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag("voice_start_cancel"),
                onClick = onDismiss
            ) {
                Text(presentation.cancelActionLabel)
            }
        }
    )
}
