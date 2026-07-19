package com.example.bamachat.ui.component.voice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPink
import com.example.bamachat.ui.voice.VoiceRuntimePresentationModel

@Composable
internal fun VoiceRuntimeDisclosure(
    presentation: VoiceRuntimePresentationModel,
    modifier: Modifier = Modifier
) {
    if (presentation.panelBadge == null &&
        presentation.connectionNotice == null &&
        presentation.microphoneNotice == null
    ) {
        return
    }

    Column(
        modifier = modifier.testTag("voice_runtime_disclosure"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presentation.panelBadge?.let { badge ->
            Surface(
                modifier = Modifier.testTag("voice_runtime_badge"),
                shape = RoundedCornerShape(50.dp),
                color = NeonPink.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.48f))
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonPink
                )
            }
        }
        presentation.connectionNotice?.let { notice ->
            Text(
                text = notice,
                modifier = Modifier.testTag("voice_runtime_connection_notice"),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonCyan,
                fontWeight = FontWeight.SemiBold
            )
        }
        presentation.microphoneNotice?.let { notice ->
            Text(
                text = notice,
                modifier = Modifier.testTag("voice_runtime_microphone_notice"),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f)
            )
        }
    }
}
