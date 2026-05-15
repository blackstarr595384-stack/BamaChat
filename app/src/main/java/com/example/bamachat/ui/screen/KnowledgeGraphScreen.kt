package com.example.bamachat.ui.screen

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.KnowledgeEdgeEntity
import com.example.bamachat.data.repository.ChatRepository
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class GraphNode(
    val label: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeGraphScreen(onBack: () -> Unit) {
    val context = LocalContext.current as? Application ?: return
    val repo = remember { ChatRepository(ChatDatabase.getDatabase(context).chatDao()) }

    val nodes = remember { mutableStateListOf<GraphNode>() }
    val edges = remember { mutableStateListOf<Pair<String, String>>() }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var selectedEdges by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val rawEdges: List<KnowledgeEdgeEntity> = repo.getKnowledgeEdges(100)
            val nodeSet = LinkedHashSet<String>()
            val edgeList = mutableListOf<Pair<String, String>>()
            rawEdges.forEach { e ->
                nodeSet.add(e.fromConcept)
                nodeSet.add(e.toConcept)
                edgeList.add(e.fromConcept to e.toConcept)
            }
            edges.clear()
            edges.addAll(edgeList)

            val labels = nodeSet.toList()
            val angleStep = (2.0 * Math.PI) / labels.size.coerceAtLeast(1)
            nodes.clear()
            nodes.addAll(labels.mapIndexed { i, label ->
                val radius = 180f + Random.Default.nextFloat() * 40f
                GraphNode(
                    label = label,
                    x = 400f + radius * cos(i * angleStep).toFloat(),
                    y = 400f + radius * sin(i * angleStep).toFloat()
                )
            })

            animateForceDirected(nodes, edges, iterations = 120)
        } catch (e: Exception) {
            errorMsg = e.message
        }
        loading = false
    }

    fun rerunLayout() {
        val angleStep = (2.0 * Math.PI) / nodes.size.coerceAtLeast(1)
        nodes.forEachIndexed { i, n ->
            val radius = 180f + Random.Default.nextFloat() * 40f
            n.x = 400f + radius * cos(i * angleStep).toFloat()
            n.y = 400f + radius * sin(i * angleStep).toFloat()
            n.vx = 0f; n.vy = 0f
        }
        animateForceDirected(nodes, edges, iterations = 120)
        selectedNode = null
        selectedEdges = emptyList()
    }

    val canvasBg = Color(0xFF1A1C1E)
    val nodeColor = Color(0xFF4F8CFF)
    val edgeColor = Color(0xFF4F8CFF).copy(alpha = 0.35f)
    val selectedNodeColor = Color(0xFF43C6AC)
    val selectedEdgeColor = Color(0xFF43C6AC).copy(alpha = 0.7f)
    val textColor = Color(0xFFEDEEF0)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Wissensgraph", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { scale = (scale - 0.15f).coerceAtLeast(0.3f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Rauszoomen")
                    }
                    IconButton(onClick = { scale = (scale + 0.15f).coerceAtMost(3f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Reinzoomen")
                    }
                    IconButton(onClick = { rerunLayout() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Neu anordnen")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Lade Wissensgraph...", color = textColor)
            }
        } else if (errorMsg != null || nodes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Keine Daten gefunden", fontSize = 16.sp, color = textColor.copy(alpha = 0.6f))
                    Spacer(Modifier.height(4.dp))
                    Text("Chatte mit der KI, um Wissen aufzubauen.", fontSize = 12.sp, color = textColor.copy(alpha = 0.4f))
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).background(canvasBg)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { tapOffset ->
                                val hit = nodes.minByOrNull { n ->
                                    val sx = (n.x * scale + offsetX)
                                    val sy = (n.y * scale + offsetY)
                                    sqrt((sx - tapOffset.x).pow(2) + (sy - tapOffset.y).pow(2))
                                }
                                if (hit != null) {
                                    val sx = (hit.x * scale + offsetX)
                                    val sy = (hit.y * scale + offsetY)
                                    val dist = sqrt((sx - tapOffset.x).pow(2) + (sy - tapOffset.y).pow(2))
                                    if (dist < 28f) {
                                        selectedNode = if (selectedNode == hit) null else hit
                                        selectedEdges = if (selectedNode == null) {
                                            emptyList()
                                        } else {
                                            edges.filter { (f, t) ->
                                                f == selectedNode?.label || t == selectedNode?.label
                                            }
                                        }
                                        return@detectTapGestures
                                    }
                                }
                                selectedNode = null
                                selectedEdges = emptyList()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    val centerX = w / 2f
                    val centerY = h / 2f

                    edges.forEach { (from, to) ->
                        val n1 = nodes.find { it.label == from }
                        val n2 = nodes.find { it.label == to }
                        if (n1 != null && n2 != null) {
                            val sx = n1.x * scale + offsetX
                            val sy = n1.y * scale + offsetY
                            val ex = n2.x * scale + offsetX
                            val ey = n2.y * scale + offsetY
                            val isSelected = selectedNode?.let { s ->
                                s.label == from || s.label == to
                            } ?: false

                            drawLine(
                                color = if (isSelected) selectedEdgeColor else edgeColor,
                                start = Offset(sx, sy),
                                end = Offset(ex, ey),
                                strokeWidth = if (isSelected) 2.5f else 1.5f
                            )
                        }
                    }

                    nodes.forEach { n ->
                        val sx = n.x * scale + offsetX
                        val sy = n.y * scale + offsetY
                        val isSelected = selectedNode == n
                        val r = if (isSelected) 14f else 10f

                        drawCircle(
                            color = if (isSelected) selectedNodeColor else nodeColor.copy(alpha = 0.85f),
                            radius = r,
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.2f),
                            radius = r,
                            center = Offset(sx + 2f, sy - 1f)
                        )
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = if (isSelected) 26f else 22f
                                isAntiAlias = true
                                setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(120, 0, 0, 0))
                            }
                            drawText(n.label, sx + r + 6f, sy + 6f, paint)
                        }
                    }
                }

                if (selectedNode != null) {
                    Surface(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF2A2D32).copy(alpha = 0.95f)
                    ) {
                        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                selectedNode!!.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = selectedNodeColor
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Verbindungen (${selectedEdges.size}):",
                                fontSize = 12.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(4.dp))
                            selectedEdges.forEach { (from, to) ->
                                val rel = "→"
                                Text(
                                    "  $from $rel $to",
                                    fontSize = 12.sp,
                                    color = textColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun animateForceDirected(
    nodes: MutableList<GraphNode>,
    edges: List<Pair<String, String>>,
    iterations: Int = 100
) {
    val repulsionStrength = 8000f
    val attractionStrength = 0.008f
    val damping = 0.85f
    val minDist = 30f

    repeat(iterations) {
        nodes.forEach { n ->
            n.vx = 0f; n.vy = 0f
            nodes.forEach { other ->
                if (other != n) {
                    val dx = n.x - other.x
                    val dy = n.y - other.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(minDist)
                    val force = repulsionStrength / (dist * dist)
                    n.vx += (dx / dist) * force
                    n.vy += (dy / dist) * force
                }
            }
        }

        edges.forEach { (from, to) ->
            val n1 = nodes.find { it.label == from }
            val n2 = nodes.find { it.label == to }
            if (n1 != null && n2 != null) {
                val dx = n2.x - n1.x
                val dy = n2.y - n1.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                n1.vx += dx * attractionStrength
                n1.vy += dy * attractionStrength
                n2.vx -= dx * attractionStrength
                n2.vy -= dy * attractionStrength
            }
        }

        nodes.forEach { n ->
            n.vx *= damping
            n.vy *= damping
            n.x += n.vx
            n.y += n.vy
        }
    }
}
