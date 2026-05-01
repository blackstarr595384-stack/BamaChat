package com.example.bamachat.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class LabBlock(
    val title: String,
    val description: String,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeLabScreen(
    onBack: () -> Unit,
    onOpenPlayground: () -> Unit
) {
    var demoMode by remember { mutableStateOf("State") }
    val demos = listOf("State", "Animation", "List")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Compose Lab", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                demos.forEach { mode ->
                    AssistChip(
                        onClick = { demoMode = mode },
                        label = { Text(mode) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compose Playground", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("BottomSheet, Dialog, Form-Validation, Navigation-Args", fontSize = 11.sp)
                    }
                    TextButton(onClick = onOpenPlayground) { Text("Öffnen") }
                }
            }
            Spacer(Modifier.height(10.dp))
            when (demoMode) {
                "State" -> StateDemo()
                "Animation" -> AnimationDemo()
                else -> ListDemo()
            }
        }
    }
}

@Composable
private fun StateDemo() {
    var counter by remember { mutableIntStateOf(1) }
    var confidence by remember { mutableFloatStateOf(0.5f) }
    var strictMode by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("State-Hoisting Demo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { counter = (counter - 1).coerceAtLeast(1) }) { Text("-") }
                Text("Antwortlänge: $counter", fontSize = 13.sp)
                Button(onClick = { counter++ }) { Text("+") }
            }
            Column {
                Text("Konfidenz: ${(confidence * 100).toInt()}%", fontSize = 12.sp)
                Slider(value = confidence, onValueChange = { confidence = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Strict Mode", fontSize = 13.sp)
                Switch(checked = strictMode, onCheckedChange = { strictMode = it })
            }
            Text(
                text = "Prompt-Vorschau: ${if (strictMode) "Kompakt, faktisch" else "Locker, unterstützend"} · Länge $counter · Score ${(confidence * 10).toInt()}/10",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun AnimationDemo() {
    var expanded by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1.04f else 0.94f,
        animationSpec = tween(durationMillis = 350),
        label = "labScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.65f,
        animationSpec = tween(durationMillis = 350),
        label = "labAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Animation Demo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF2F5EF7), Color(0xFF7D49D8), Color(0xFFC65E8A))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("Tippen für Transition", color = Color.White, fontWeight = FontWeight.Medium)
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    "Das ist ein AnimatedVisibility-Block. Genau so kannst du Advanced-Optionen ein-/ausblenden.",
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ListDemo() {
    val blocks = listOf(
        LabBlock(
            "Agent Builder",
            "Dynamische Konfig-Karten mit StateFlow-Bindung.",
            icon = { Icon(Icons.Default.Memory, contentDescription = null) }
        ),
        LabBlock(
            "Streaming Output",
            "SSE-Text live in Bubble rendern und throttled speichern.",
            icon = { Icon(Icons.Default.Bolt, contentDescription = null) }
        ),
        LabBlock(
            "Visual Polish",
            "Header-Chips, Gradients, Motion und Struktur.",
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(blocks) { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        item.icon()
                    }
                    Column {
                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}
