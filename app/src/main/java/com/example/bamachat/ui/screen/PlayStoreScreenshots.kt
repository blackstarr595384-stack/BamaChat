package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.ui.theme.Primary

/**
 * Play Store Screenshot Components for various device formats
 * Screenshot dimensions (as per Google Play requirement):
 * - 5": 1080 x 1920 px
 * - 5.8": 1440 x 2560 px
 * - 6.7": 1440 x 3120 px
 * - 7": 1600 x 2560 px
 */

@Composable
fun PlayStoreScreenshot1() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Primary,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "BamaFlow",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(48.dp)
                )
            }

            Text(
                text = "BamaFlow",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
            )

            Text(
                text = "Dein KI-Workspace statt nur ein KI-Chat",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color(0xFF1F1F1F),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Agenten, Workspaces, Tools und Zusammenarbeit in einer einzigen Android-App.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF808080),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun PlayStoreScreenshot2() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Arbeiten statt nur chatten",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary
            )

            FeatureRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Mehrere KI-Modelle",
                description = "OpenRouter, Groq, Cerebras, Together, Gemini und Ollama in einem Chat."
            )

            FeatureRow(
                icon = Icons.Default.SmartToy,
                title = "Eigene Agenten",
                description = "Personas, Prompts und Trainingsbeispiele für deinen Stil und deinen Workflow."
            )

            FeatureRow(
                icon = Icons.Default.Groups,
                title = "Team-Kollaboration",
                description = "Workspaces, Präsenz und gemeinsame Sessions direkt in der App."
            )
        }
    }
}

@Composable
fun PlayStoreScreenshot3() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Tools, Wissen, Workflows",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary
            )

            FeatureRow(
                icon = Icons.Default.Apps,
                title = "Workspace-Hub",
                description = "Mini-Apps, Projektflächen und Fokus-Tools an einem Ort."
            )

            FeatureRow(
                icon = Icons.Default.Speed,
                title = "MCP & Recherche",
                description = "Externe Tools anbinden und Web-Recherche direkt in den Chat holen."
            )

            FeatureRow(
                icon = Icons.Default.Star,
                title = "Voice, Bilder, Dateien",
                description = "Multimodale Eingaben ohne Medienbruch für alltägliche KI-Arbeit."
            )
        }
    }
}

@Composable
fun PlayStoreScreenshot4() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sofort loslegen",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = Primary,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Als Gast testen oder mit Konto für Cloud-Sync und Zusammenarbeit starten.",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF808080),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 40.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "Jetzt im Google Play Store testen",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFE8EFFF),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Primary,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(32.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1F1F)
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color(0xFF808080)
                )
            }
        }
    }
}

// Preview Composables can be viewed in Android Studio Preview panel
// by uncommenting @Preview annotations when viewing the file directly

