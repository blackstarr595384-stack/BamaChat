package com.example.bamachat.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.viewmodel.AgentNotebookViewModel
import com.example.bamachat.ui.viewmodel.AgentStep
import com.example.bamachat.ui.viewmodel.StepStatus

private val GradientTop = Color(0xFF08111F)
private val GradientMid = Color(0xFF132844)
private val GradientBot = Color(0xFF18385E)
private val SurfaceColor = Color(0xFF213857).copy(alpha = 0.72f)
private val AccentColor = Color(0xFFB9CCFF)
private val TextSecondary = Color(0xFFD8E4FF)
private val CardBorder = Color(0xFF2F4A6E).copy(alpha = 0.6f)

private val exampleGoals = listOf(
    "Analysiere mein Projekt und finde Optimierungspotential",
    "Erstelle eine Zusammenfassung meiner letzten 10 Chats",
    "Durchsuche das Web nach den neuesten KI-Trends",
    "Überprüfe meinen Code auf Sicherheitslücken"
)

@Composable
fun AgentNotebookScreen(
    onBack: () -> Unit,
    viewModel: AgentNotebookViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientTop, GradientMid, GradientBot)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Agent-Notebook",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!state.isRunning && state.result == null) {
                    GoalInputSection(
                        goal = state.goal,
                        onGoalChange = viewModel::setGoal,
                        onStart = viewModel::startAgent,
                        error = state.error
                    )

                    if (state.steps.isEmpty()) {
                        ExampleGoalsSection(onSelect = { goal ->
                            viewModel.setGoal(goal)
                        })
                    }
                }

                if (state.isRunning || state.steps.isNotEmpty()) {
                    val listState = rememberLazyListState()

                    LaunchedEffect(state.steps.size) {
                        if (state.steps.isNotEmpty()) {
                            listState.animateScrollToItem(state.steps.size - 1)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Agent-Log",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            state.steps.forEach { step ->
                                StepCard(step = step, isRunningState = state.isRunning)
                            }
                        }
                    }
                }

                if (state.isRunning) {
                    Button(
                        onClick = viewModel::stopAgent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C2F3A).copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Agent stoppen", fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }

                if (state.result != null) {
                    ResultSection(
                        result = state.result!!,
                        onReset = viewModel::reset,
                        onNewTask = {
                            viewModel.reset()
                        }
                    )
                }

                if (state.error != null && !state.isRunning && state.result == null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF9C2F3A).copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9C2F3A).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                            Text(
                                text = state.error ?: "",
                                color = Color(0xFFFFC8C8),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GoalInputSection(
    goal: String,
    onGoalChange: (String) -> Unit,
    onStart: () -> Unit,
    error: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Was soll ich für dich erledigen?",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = goal,
                onValueChange = onGoalChange,
                placeholder = {
                    Text(
                        "Beschreibe dein Ziel...",
                        color = TextSecondary.copy(alpha = 0.4f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = CardBorder,
                    cursorColor = AccentColor
                )
            )

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(14.dp),
                enabled = goal.isNotBlank()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Agent starten",
                    fontWeight = FontWeight.SemiBold,
                    color = AccentColor
                )
            }
        }
    }
}

@Composable
private fun ExampleGoalsSection(onSelect: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Beispielziele",
                color = TextSecondary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            exampleGoals.forEach { example ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(example) },
                    shape = RoundedCornerShape(10.dp),
                    color = AccentColor.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = example,
                        color = AccentColor.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: AgentStep, isRunningState: Boolean) {
    val isActive = step.status == StepStatus.RUNNING

    val borderAlpha by animateColorAsState(
        targetValue = when (step.status) {
            StepStatus.RUNNING -> AccentColor.copy(alpha = 0.6f)
            StepStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.5f)
            StepStatus.FAILED -> Color(0xFFFF6B6B).copy(alpha = 0.5f)
            StepStatus.PENDING -> CardBorder
        },
        animationSpec = tween(300)
    )

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isActive) Modifier.alpha(pulseAlpha) else Modifier
                ),
            shape = RoundedCornerShape(10.dp),
            color = when (step.status) {
                StepStatus.RUNNING -> AccentColor.copy(alpha = 0.1f)
                StepStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                StepStatus.FAILED -> Color(0xFFFF6B6B).copy(alpha = 0.08f)
                StepStatus.PENDING -> Color.White.copy(alpha = 0.04f)
            },
            border = androidx.compose.foundation.BorderStroke(1.dp, borderAlpha)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when (step.status) {
                                StepStatus.RUNNING -> AccentColor.copy(alpha = 0.2f)
                                StepStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                StepStatus.FAILED -> Color(0xFFFF6B6B).copy(alpha = 0.2f)
                                StepStatus.PENDING -> Color.White.copy(alpha = 0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (step.status) {
                        StepStatus.RUNNING -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentColor,
                            strokeWidth = 2.dp
                        )
                        StepStatus.COMPLETED -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        StepStatus.FAILED -> Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(16.dp)
                        )
                        StepStatus.PENDING -> Icon(
                            Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        step.toolUsed?.let { tool ->
                            Text(text = tool, fontSize = 12.sp)
                        }
                        Text(
                            text = step.description,
                            color = when (step.status) {
                                StepStatus.COMPLETED -> Color(0xFFA5D6A7)
                                StepStatus.FAILED -> Color(0xFFFFCDD2)
                                else -> Color.White
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                    step.output?.let { output ->
                        Text(
                            text = output,
                            color = TextSecondary.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSection(
    result: String,
    onReset: () -> Unit,
    onNewTask: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1B5E20).copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Aufgabe abgeschlossen",
                    color = Color(0xFFA5D6A7),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = result,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )

            Button(
                onClick = onNewTask,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Neue Aufgabe", fontWeight = FontWeight.SemiBold, color = AccentColor)
            }
        }
    }
}
