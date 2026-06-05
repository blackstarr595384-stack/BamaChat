package com.example.bamachat.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GradientTop = Color(0xFF08111F)
private val GradientMid = Color(0xFF132844)
private val GradientBot = Color(0xFF18385E)
private val SurfaceColor = Color(0xFF213857).copy(alpha = 0.72f)
private val AccentColor = Color(0xFFB9CCFF)
private val TextSecondary = Color(0xFFD8E4FF)
private val CardBorder = Color(0xFF2F4A6E).copy(alpha = 0.6f)

private data class FlowNode(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val category: String
)

private data class WorkflowStep(
    val node: FlowNode,
    val order: Int
)

private val availableNodes = listOf(
    FlowNode("web_search", "Web Search", "Durchsuche das Web nach Informationen", Icons.Default.Language, "Recherche"),
    FlowNode("web_fetch", "Web Fetch", "Rufe Webseiten-Inhalte ab", Icons.Default.Web, "Recherche"),
    FlowNode("read_file", "Read File", "Lese lokale Dateien ein", Icons.Default.Edit, "Dateien"),
    FlowNode("write_file", "Write File", "Schreibe Inhalte in Dateien", Icons.Default.Edit, "Dateien"),
    FlowNode("run_terminal", "Run Terminal", "Führe Terminal-Befehle aus", Icons.Default.Terminal, "System"),
    FlowNode("code_review", "Code Review", "Prüfe Code auf Qualität und Bugs", Icons.Default.Code, "Entwicklung"),
    FlowNode("git_status", "Git Status", "Zeige Git-Repository-Status an", Icons.Default.Code, "Entwicklung"),
    FlowNode("send_chat", "Send Chat", "Sende eine Chat-Nachricht", Icons.AutoMirrored.Filled.Send, "Kommunikation"),
    FlowNode("generate_image", "Generate Image", "Erstelle ein Bild per KI", Icons.Default.Image, "Kreativ"),
    FlowNode("run_automation", "Run Automation", "Starte eine automatisierte Abfolge", Icons.Default.Cloud, "System")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowBuilderScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val workflowSteps = remember { mutableStateListOf<WorkflowStep>() }
    var isRunning by remember { mutableStateOf(false) }
    var runStatus by remember { mutableStateOf("") }
    var currentStepIndex by remember { mutableIntStateOf(-1) }

    val filteredNodes = remember(searchQuery) {
        if (searchQuery.isBlank()) availableNodes
        else availableNodes.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
        }
    }

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
                    text = "Flow-Builder",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Nodes durchsuchen...", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = CardBorder,
                    cursorColor = AccentColor
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = AccentColor,
                edgePadding = 16.dp
            ) {
                listOf("Katalog", "Workflow").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = if (index == 1) "$title (${workflowSteps.size})" else title,
                                color = if (selectedTab == index) AccentColor else TextSecondary.copy(alpha = 0.6f),
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> CatalogTab(nodes = filteredNodes, onAdd = { node ->
                    workflowSteps.add(WorkflowStep(node, workflowSteps.size + 1))
                })
                1 -> WorkflowTab(
                    steps = workflowSteps,
                    isRunning = isRunning,
                    runStatus = runStatus,
                    currentStepIndex = currentStepIndex,
                    onMoveUp = { index ->
                        if (index > 0) {
                            val item = workflowSteps.removeAt(index)
                            workflowSteps.add(index - 1, item)
                        }
                    },
                    onMoveDown = { index ->
                        if (index < workflowSteps.size - 1) {
                            val item = workflowSteps.removeAt(index)
                            workflowSteps.add(index + 1, item)
                        }
                    },
                    onRemove = { index -> workflowSteps.removeAt(index) },
                    onRun = {
                        isRunning = true
                        runStatus = "Workflow wird ausgeführt..."
                        currentStepIndex = 0
                    }
                )
            }
        }
    }
}

@Composable
private fun CatalogTab(nodes: List<FlowNode>, onAdd: (FlowNode) -> Unit) {
    if (nodes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Keine Nodes gefunden.",
                color = TextSecondary.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(nodes, key = { it.id }) { node ->
                NodeCard(node = node, onAdd = { onAdd(node) })
            }
        }
    }
}

@Composable
private fun NodeCard(node: FlowNode, onAdd: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = node.icon,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = node.name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = node.description,
                color = TextSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hinzufügen", fontSize = 11.sp, color = AccentColor)
            }
        }
    }
}

@Composable
private fun WorkflowTab(
    steps: List<WorkflowStep>,
    isRunning: Boolean,
    runStatus: String,
    currentStepIndex: Int,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onRun: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Workflow-Ablauf",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (steps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Noch keine Schritte hinzugefügt",
                        color = TextSecondary.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Wähle Nodes aus dem Katalog aus",
                        color = TextSecondary.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(steps) { index, step ->
                    val isCurrent = index == currentStepIndex && isRunning
                    val bgColor by animateColorAsState(
                        targetValue = if (isCurrent) AccentColor.copy(alpha = 0.15f)
                        else SurfaceColor,
                        animationSpec = tween(300)
                    )

                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrent) AccentColor.copy(alpha = 0.5f) else CardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${step.order}.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(24.dp)
                            )
                            Icon(
                                imageVector = step.node.icon,
                                contentDescription = null,
                                tint = AccentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.node.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = step.node.description,
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(
                                onClick = { onMoveUp(index) },
                                enabled = index > 0 && !isRunning
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Nach oben",
                                    tint = if (index > 0 && !isRunning) TextSecondary else TextSecondary.copy(alpha = 0.3f)
                                )
                            }
                            IconButton(
                                onClick = { onMoveDown(index) },
                                enabled = index < steps.size - 1 && !isRunning
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Nach unten",
                                    tint = if (index < steps.size - 1 && !isRunning) TextSecondary else TextSecondary.copy(alpha = 0.3f)
                                )
                            }
                            IconButton(
                                onClick = { onRemove(index) },
                                enabled = !isRunning
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Entfernen",
                                    tint = if (!isRunning) Color(0xFFFF6B6B) else TextSecondary.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (runStatus.isNotBlank()) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AccentColor.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = runStatus,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Button(
            onClick = onRun,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = steps.isNotEmpty() && !isRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRunning) "Wird ausgeführt..." else "Workflow ausführen",
                fontWeight = FontWeight.SemiBold,
                color = AccentColor
            )
        }
    }
}
