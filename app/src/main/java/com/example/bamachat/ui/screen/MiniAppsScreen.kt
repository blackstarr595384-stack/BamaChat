package com.example.bamachat.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlin.math.abs
import kotlin.random.Random

enum class MiniApp(val displayName: String, val emoji: String, val description: String) {
    BROWSER("Mini-Browser", "🌐", "Im Web surfen"),
    DOODLE("Doodle-Pad", "🎨", "Zeichnen & Skizzieren"),
    GAME_2048("2048", "🎮", "Klassisches Zahlen-Spiel"),
    NOTES("Notizen", "📝", "Schnelle Notizen")
}

private data class MiniAppMood(
    val top: Color,
    val bottom: Color,
    val appBar: Color,
    val card: Color
)

private fun miniAppMood(app: MiniApp?): MiniAppMood = when (app) {
    MiniApp.BROWSER -> MiniAppMood(
        top = Color(0xFF0D2430),
        bottom = Color(0xFF113446),
        appBar = Color(0xFF1C5A73),
        card = Color(0xFF1A3D4F)
    )
    MiniApp.DOODLE -> MiniAppMood(
        top = Color(0xFF2C1236),
        bottom = Color(0xFF3A1950),
        appBar = Color(0xFF7F3AB5),
        card = Color(0xFF4A266A)
    )
    MiniApp.GAME_2048 -> MiniAppMood(
        top = Color(0xFF2A1E12),
        bottom = Color(0xFF382713),
        appBar = Color(0xFFB46B24),
        card = Color(0xFF4A3321)
    )
    MiniApp.NOTES -> MiniAppMood(
        top = Color(0xFF112616),
        bottom = Color(0xFF183A22),
        appBar = Color(0xFF2D8F56),
        card = Color(0xFF2A5136)
    )
    null -> MiniAppMood(
        top = Color(0xFF0F1828),
        bottom = Color(0xFF1A2138),
        appBar = Color(0xFF3D5AA8),
        card = Color(0xFF253556)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppsScreen(
    themeColor: Color,
    onClose: () -> Unit
) {
    var currentApp by remember { mutableStateOf<MiniApp?>(null) }
    val mood = miniAppMood(currentApp)
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(mood.top, mood.bottom)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentApp?.let { "${it.emoji} ${it.displayName}" } ?: "🎯 Mini-Apps",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentApp != null) currentApp = null
                        else onClose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = mood.appBar.copy(alpha = 0.95f),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(backgroundGradient)) {
            when (currentApp) {
                null -> AppsHub(themeColor = mood.appBar, cardColor = mood.card) { currentApp = it }
                MiniApp.BROWSER -> MiniBrowser(mood.appBar)
                MiniApp.DOODLE -> DoodlePad(mood.appBar)
                MiniApp.GAME_2048 -> Game2048(mood.appBar)
                MiniApp.NOTES -> NotesApp(mood.appBar)
            }
        }
    }
}

@Composable
private fun AppsHub(themeColor: Color, cardColor: Color, onSelect: (MiniApp) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Was hast du heute Lust auf?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Diese Apps funktionieren komplett offline — auch wenn die KI mal Pause macht 😉",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))

        MiniApp.entries.forEach { app ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = themeColor)
                    .clickable { onSelect(app) },
                shape = RoundedCornerShape(20.dp),
                color = cardColor
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Brush.radialGradient(listOf(themeColor.copy(alpha = 0.4f), themeColor.copy(alpha = 0.1f))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(app.emoji, fontSize = 32.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.displayName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(app.description, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = themeColor, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

// ===== Mini-Browser =====
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiniBrowser(themeColor: Color) {
    var url by remember { mutableStateOf("https://www.google.com") }
    var inputUrl by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // URL Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1A1C1E),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { webView?.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, "Neu laden", tint = Color.White)
                    }
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("URL oder Suche...", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val target = if (inputUrl.startsWith("http")) inputUrl
                            else if (inputUrl.contains(".") && !inputUrl.contains(" ")) "https://$inputUrl"
                            else "https://www.google.com/search?q=${java.net.URLEncoder.encode(inputUrl, "UTF-8")}"
                            url = target
                            webView?.loadUrl(target)
                        }),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = themeColor
                    )
                }
                if (pageTitle.isNotBlank()) {
                    Text(
                        pageTitle,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1
                    )
                }
            }
        }
        // WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    setBackgroundColor(AndroidColor.WHITE)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                            inputUrl = url ?: ""
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title ?: ""
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            }
        )
    }
}

// ===== Doodle-Pad =====
@Composable
private fun DoodlePad(themeColor: Color) {
    val paths = remember { mutableStateListOf<DoodlePath>() }
    var currentPath by remember { mutableStateOf<DoodlePath?>(null) }
    var selectedColor by remember { mutableStateOf(Color.White) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }

    val palette = listOf(
        Color.White, Color.Red, Color(0xFFFFA500), Color.Yellow,
        Color(0xFF00FF00), Color.Cyan, Color.Blue, Color(0xFF9C27B0),
        Color(0xFFE91E63), Color(0xFF795548), Color.Black
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(color = Color(0xFF1A1C1E), shadowElevation = 4.dp) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    palette.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(if (selectedColor == color) 36.dp else 28.dp)
                                .background(color, CircleShape)
                                .clickable { selectedColor = color }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pinsel:", color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 2f..40f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = themeColor,
                            activeTrackColor = themeColor
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { paths.clear() },
                        modifier = Modifier.background(Color.Red.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, "Löschen", tint = Color.White)
                    }
                    IconButton(
                        onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) },
                        modifier = Modifier.background(Color.Yellow.copy(alpha = 0.2f), CircleShape)
                    ) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.Undo, "Rückgängig", tint = Color.White)
                    }
                }
            }
        }
        // Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF22252A))
                .pointerInput(selectedColor, strokeWidth) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = DoodlePath(
                                color = selectedColor,
                                strokeWidth = strokeWidth,
                                points = mutableListOf(offset)
                            )
                        },
                        onDrag = { change, _ ->
                            currentPath?.points?.add(change.position)
                            // Force recomposition
                            currentPath = currentPath?.copy(points = currentPath!!.points.toMutableList())
                        },
                        onDragEnd = {
                            currentPath?.let { paths.add(it) }
                            currentPath = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                paths.forEach { p ->
                    drawDoodlePath(p)
                }
                currentPath?.let { drawDoodlePath(it) }
            }
        }
    }
}

data class DoodlePath(
    val color: Color,
    val strokeWidth: Float,
    val points: MutableList<Offset>
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoodlePath(p: DoodlePath) {
    if (p.points.size < 2) return
    val path = Path()
    path.moveTo(p.points[0].x, p.points[0].y)
    for (i in 1 until p.points.size) {
        path.lineTo(p.points[i].x, p.points[i].y)
    }
    drawPath(
        path = path,
        color = p.color,
        style = Stroke(width = p.strokeWidth, cap = StrokeCap.Round)
    )
}

// ===== 2048 Game =====
@Composable
private fun Game2048(themeColor: Color) {
    val grid = remember { mutableStateOf(spawn2048(spawn2048(Array(4) { IntArray(4) }))) }
    var score by remember { mutableIntStateOf(0) }
    val gameOver = remember { mutableStateOf(false) }
    val won = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Punkte", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text("$score", color = themeColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    grid.value = spawn2048(spawn2048(Array(4) { IntArray(4) }))
                    score = 0
                    gameOver.value = false
                    won.value = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) {
                Text("Neu", color = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFF161719), RoundedCornerShape(12.dp))
                .padding(8.dp)
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        if (gameOver.value) return@detectDragGestures
                        val (dx, dy) = drag
                        if (abs(dx) < 30 && abs(dy) < 30) return@detectDragGestures
                        val direction = when {
                            abs(dx) > abs(dy) && dx > 0 -> Direction.RIGHT
                            abs(dx) > abs(dy) && dx < 0 -> Direction.LEFT
                            dy > 0 -> Direction.DOWN
                            else -> Direction.UP
                        }
                        val (newGrid, gainedScore) = move2048(grid.value, direction)
                        if (!sameGrid(grid.value, newGrid)) {
                            grid.value = spawn2048(newGrid)
                            score += gainedScore
                            if (newGrid.any { row -> row.any { it >= 2048 } }) won.value = true
                            if (isGameOver(grid.value)) gameOver.value = true
                        }
                    }
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0..3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        for (col in 0..3) {
                            Tile2048(grid.value[row][col], themeColor, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
        Text(
            "Wische um zu spielen!",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        if (gameOver.value) {
            Text("Game Over! 😅", color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (won.value) {
            Text("Du hast 2048 erreicht! 🎉", color = themeColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Tile2048(value: Int, themeColor: Color, modifier: Modifier = Modifier) {
    val color = tileColor2048(value, themeColor)
    val textColor = if (value <= 4) Color.Black else Color.White
    Box(
        modifier = modifier.background(color, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (value > 0) {
            Text(
                "$value",
                color = textColor,
                fontSize = if (value < 100) 24.sp else if (value < 1000) 20.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun tileColor2048(v: Int, theme: Color): Color = when (v) {
    0 -> Color(0xFF22252A)
    2 -> Color(0xFFEEE4DA)
    4 -> Color(0xFFEDE0C8)
    8 -> Color(0xFFF2B179)
    16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F)
    64 -> Color(0xFFF65E3B)
    128 -> Color(0xFFEDCF72)
    256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F)
    2048 -> theme
    else -> Color(0xFF3C3A32)
}

enum class Direction { UP, DOWN, LEFT, RIGHT }

private fun move2048(grid: Array<IntArray>, dir: Direction): Pair<Array<IntArray>, Int> {
    val newGrid = Array(4) { r -> IntArray(4) { c -> grid[r][c] } }
    var score = 0
    fun mergeRow(row: IntArray): Pair<IntArray, Int> {
        var s = 0
        val filtered = row.filter { it != 0 }.toMutableList()
        var i = 0
        while (i < filtered.size - 1) {
            if (filtered[i] == filtered[i + 1]) {
                filtered[i] *= 2
                s += filtered[i]
                filtered.removeAt(i + 1)
            }
            i++
        }
        while (filtered.size < 4) filtered.add(0)
        return filtered.toIntArray() to s
    }
    when (dir) {
        Direction.LEFT -> {
            for (r in 0..3) {
                val (m, s) = mergeRow(newGrid[r])
                newGrid[r] = m
                score += s
            }
        }
        Direction.RIGHT -> {
            for (r in 0..3) {
                val reversed = newGrid[r].reversedArray()
                val (m, s) = mergeRow(reversed)
                newGrid[r] = m.reversedArray()
                score += s
            }
        }
        Direction.UP -> {
            for (c in 0..3) {
                val col = IntArray(4) { newGrid[it][c] }
                val (m, s) = mergeRow(col)
                for (r in 0..3) newGrid[r][c] = m[r]
                score += s
            }
        }
        Direction.DOWN -> {
            for (c in 0..3) {
                val col = IntArray(4) { newGrid[3 - it][c] }
                val (m, s) = mergeRow(col)
                for (r in 0..3) newGrid[3 - r][c] = m[r]
                score += s
            }
        }
    }
    return newGrid to score
}

private fun spawn2048(grid: Array<IntArray>): Array<IntArray> {
    val empty = mutableListOf<Pair<Int, Int>>()
    for (r in 0..3) for (c in 0..3) if (grid[r][c] == 0) empty.add(r to c)
    if (empty.isEmpty()) return grid
    val (r, c) = empty.random()
    grid[r][c] = if (Random.nextFloat() < 0.9f) 2 else 4
    return grid
}

private fun sameGrid(a: Array<IntArray>, b: Array<IntArray>): Boolean {
    for (r in 0..3) for (c in 0..3) if (a[r][c] != b[r][c]) return false
    return true
}

private fun isGameOver(grid: Array<IntArray>): Boolean {
    for (r in 0..3) for (c in 0..3) {
        if (grid[r][c] == 0) return false
        if (c < 3 && grid[r][c] == grid[r][c + 1]) return false
        if (r < 3 && grid[r][c] == grid[r + 1][c]) return false
    }
    return true
}

// ===== Notes App =====
@Composable
private fun NotesApp(themeColor: Color) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("notes", Context.MODE_PRIVATE) }
    var notes by remember { mutableStateOf(loadNotes(prefs)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newNoteText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        if (notes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📝", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Text("Noch keine Notizen", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Tippe + um eine zu erstellen", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.first }) { note ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF22252A)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                note.second,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    notes = notes.filter { it.first != note.first }
                                    saveNotes(prefs, notes)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Löschen", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true; newNoteText = "" },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = themeColor
        ) {
            Icon(Icons.Default.Add, "Neue Notiz", tint = Color.White)
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Neue Notiz") },
            text = {
                OutlinedTextField(
                    value = newNoteText,
                    onValueChange = { newNoteText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder = { Text("Schreib was...") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newNoteText.isNotBlank()) {
                        val newList = notes + (System.currentTimeMillis().toString() to newNoteText)
                        notes = newList
                        saveNotes(prefs, newList)
                    }
                    showAddDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

private fun loadNotes(prefs: android.content.SharedPreferences): List<Pair<String, String>> {
    val json = prefs.getString("notes_json", "[]") ?: "[]"
    return try {
        com.google.gson.Gson().fromJson(json, Array<Array<String>>::class.java)
            .map { it[0] to it[1] }
    } catch (_: Exception) { emptyList() }
}

private fun saveNotes(prefs: android.content.SharedPreferences, notes: List<Pair<String, String>>) {
    val arr = notes.map { arrayOf(it.first, it.second) }.toTypedArray()
    prefs.edit().putString("notes_json", com.google.gson.Gson().toJson(arr)).apply()
}
