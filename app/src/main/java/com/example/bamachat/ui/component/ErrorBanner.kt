package com.example.bamachat.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.util.UserErrorMessage

/**
 * Enhanced Error Banner mit automatischem Icon, Title + Suggestion
 */
@Composable
fun ErrorBanner(
    errorMessage: UserErrorMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val (bgColor, icon, iconColor) = when (errorMessage.code) {
        "AUTH_ERROR" -> Triple(Color(0x33FF6B6B), Icons.Default.Error, Color(0xFFFF6B6B))
        "NETWORK_ERROR" -> Triple(Color(0x334ECDC4), Icons.Default.Warning, Color(0xFF4ECDC4))
        "QUOTA_EXCEEDED" -> Triple(Color(0x33FFD93D), Icons.Default.Warning, Color(0xFFFFD93D))
        "API_ERROR" -> Triple(Color(0x336BCB77), Icons.Default.Refresh, Color(0xFF6BCB77))
        "TIMEOUT" -> Triple(Color(0x334ECDC4), Icons.Default.Warning, Color(0xFF4ECDC4))
        else -> Triple(Color(0x33A8DADC), Icons.Default.Info, Color(0xFFA8DADC))
    }

    val animatedBgColor by animateColorAsState(targetValue = bgColor, label = "error_bg")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = animatedBgColor,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.padding(top = 2.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = errorMessage.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = errorMessage.description,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = errorMessage.suggestion,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic
            )

            // Action Row
            if (errorMessage.isRetryable && onRetry != null && errorMessage.actionLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                            tint = Color.White
                        )
                        Text(
                            text = errorMessage.actionLabel,
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .padding(-8.dp)
                .align(Alignment.Top)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Schließen",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
            )
        }
    }
}

/**
 * Compact Error Info für Quick-Display
 */
@Composable
fun CompactErrorInfo(
    errorMessage: UserErrorMessage,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF3A3A4A),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = errorMessage.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF6B6B)
            )
            Text(
                text = errorMessage.suggestion,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
