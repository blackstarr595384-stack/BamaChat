package com.example.bamachat.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class CompactTextAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val color: Color? = null
)

@Composable
fun CompactTextActionRow(
    actions: List<CompactTextAction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            TextButton(
                enabled = action.enabled,
                onClick = action.onClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = action.color ?: LocalContentColor.current
                )
            ) {
                Text(action.label)
            }
        }
    }
}
