package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(
    isAuthenticated: Boolean,
    onOpenChat: () -> Unit,
    onOpenAuth: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onOpenHelp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF111827), Color(0xFF1D3557), Color(0xFF1E3A5F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.1f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "BamaChat",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Dein KI-Workspace für Chat, Personas, Multimodal und Collaboration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(8.dp))

                if (isAuthenticated) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenChat
                    ) { Text("Zum BamaHub") }
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenAuth
                    ) { Text("Anmelden oder Registrieren") }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onContinueAsGuest
                    ) { Text("Als Gast starten") }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenHelp
                ) { Text("Hilfe & Anleitung") }
            }
        }
    }
}
