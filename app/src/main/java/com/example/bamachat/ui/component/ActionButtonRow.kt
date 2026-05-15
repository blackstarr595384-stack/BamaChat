package com.example.bamachat.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActionButtonRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    primaryModifier: Modifier = Modifier,
    secondaryModifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = primaryEnabled,
            onClick = onPrimary,
            modifier = primaryModifier
        ) {
            Text(primaryLabel)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Button(
                enabled = secondaryEnabled,
                onClick = onSecondary,
                modifier = secondaryModifier
            ) {
                Text(secondaryLabel)
            }
        }
    }
}
