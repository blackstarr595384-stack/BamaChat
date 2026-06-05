package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingAPIKeySetupScreen(
    onComplete: (openRouterKey: String, groqKey: String) -> Unit,
    onSkip: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var openRouterKey by remember { mutableStateOf("") }
    var groqKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F1424), Color(0xFF161B26))
                )
            )
    ) {
        // Progress bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFF3A3A4A))
        ) {
            repeat(2) { index ->
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (index <= step) Color(0xFF4F8CFF) else Color.Transparent)
                )
                if (index < 1) Spacer(modifier = Modifier.width(1.dp))
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (step == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Schritt 1: OpenRouter API-Key",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "OpenRouter bietet Zugriff auf multiple KI-Modelle (GPT-4, Claude, Gemma, etc.).",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            "1. Gehe zu https://openrouter.ai\n2. Melde dich an und erstelle einen API-Key\n3. Kopiere deinen Key hier ein",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = openRouterKey,
                            onValueChange = { openRouterKey = it },
                            label = { Text("OpenRouter API-Key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Schritt 2: Groq API-Key (Optional)",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Groq ist sehr schnell und kostenlos. Super für schnelle Responses.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            "1. Gehe zu https://console.groq.com\n2. Erstelle einen API-Key\n3. Oder überspringe diesen Schritt (du kannst ihn später hinzufügen)",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = groqKey,
                            onValueChange = { groqKey = it },
                            label = { Text("Groq API-Key (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                }
            }
        }

        // Bottom Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onSkip,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Überspringen")
            }

            Button(
                onClick = {
                    if (step == 0 && openRouterKey.isNotBlank()) {
                        step = 1
                    } else if (step == 1) {
                        onComplete(openRouterKey, groqKey)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = if (step == 0) openRouterKey.isNotBlank() else true
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(if (step == 0) "Weiter" else "Fertig")
                    Icon(
                        imageVector = if (step == 0) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.Check,
                        contentDescription = null
                    )
                }
            }
        }
    }
}
