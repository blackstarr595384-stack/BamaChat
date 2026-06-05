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
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    primaryButtonModifier: Modifier = Modifier,
    secondaryButtonModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            enabled = primaryEnabled,
            onClick = onPrimary,
            modifier = primaryButtonModifier
        ) {
            Text(primaryLabel)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Button(
                enabled = secondaryEnabled,
                onClick = onSecondary,
                modifier = secondaryButtonModifier
            ) {
                Text(secondaryLabel)
            }
        }
    }
}
