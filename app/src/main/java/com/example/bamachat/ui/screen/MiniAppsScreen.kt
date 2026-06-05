@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.bamachat.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.speech.RecognizerIntent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.ui.component.CompactTextAction
import com.example.bamachat.ui.component.CompactTextActionRow
import com.example.bamachat.ui.component.PhotoStudioApp
import com.example.bamachat.util.AutomationCatalog
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

enum class MiniAppCategory(val label: String) {
    PRODUCTIVITY("Produktiv"),
    CREATIVE("Kreativ"),
    UTILITY("Utility"),
    KNOWLEDGE("Knowledge")
}

@Composable
private fun MiniAppHeroCard() {
    Surface(
        shape = MiniAppsDesignTokens.heroRadius,
        color = Color.White.copy(alpha = 0.1f),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Mini-Apps V2",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Discover-Ansicht, Live-Cards, Favoriten, Swipe-Management und neue AI-Tools.",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun MiniAppSectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp
    )
}

internal enum class MiniAppStatusTone {
    INFO,
    SUCCESS,
    ERROR
}

@Composable
internal fun MiniAppStatusBanner(
    message: String,
    tone: MiniAppStatusTone,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    if (message.isBlank()) return
    val background = when (tone) {
        MiniAppStatusTone.INFO -> Color(0xFF2A3E5C).copy(alpha = 0.72f)
        MiniAppStatusTone.SUCCESS -> Color(0xFF1F4B38).copy(alpha = 0.74f)
        MiniAppStatusTone.ERROR -> Color(0xFF5A2B2B).copy(alpha = 0.74f)
    }
    val icon = when (tone) {
        MiniAppStatusTone.INFO -> Icons.Default.Info
        MiniAppStatusTone.SUCCESS -> Icons.Default.CheckCircle
        MiniAppStatusTone.ERROR -> Icons.Default.Warning
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = background,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(message, color = Color.White, fontSize = 12.sp)
            }
            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
            }
        }
    }
}

@Composable
private fun MiniAppsEmptyState(
    query: String,
    filterLabel: String,
    onReset: () -> Unit
) {
    Surface(
        shape = MiniAppsDesignTokens.surfaceRadius,
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("🔎 Keine Treffer", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "Filter: $filterLabel${if (query.isNotBlank()) " • Suche: \"$query\"" else ""}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
            TextButton(onClick = onReset) {
                Text("Filter & Suche zurücksetzen")
            }
        }
    }
}

@Composable
private fun MiniAppCardSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "miniapp-skeleton")
    val alpha = transition.animateFloat(
        initialValue = 0.26f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950),
            repeatMode = RepeatMode.Reverse
        ),
        label = "miniapp-skeleton-alpha"
    )
    Surface(
        modifier = modifier.height(122.dp),
        shape = MiniAppsDesignTokens.surfaceRadius,
        color = Color.White.copy(alpha = alpha.value)
    ) {}
}

@Composable
private fun MiniAppSpotlightCard(
    app: MiniApp,
    accent: Color,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(240.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = MiniAppsDesignTokens.surfaceRadius,
        color = Color.White.copy(alpha = 0.12f),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    app.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (favorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD76E),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                app.description,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                maxLines = 2
            )
            AssistChip(
                onClick = onClick,
                label = { Text("${app.status.label} • ${app.category.label}") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = accent.copy(alpha = 0.3f),
                    labelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun MiniAppLiveCard(
    app: MiniApp,
    accent: Color,
    favorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(MiniAppsDesignTokens.cardRadius)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        shape = MiniAppsDesignTokens.cardRadius,
        color = Color.White.copy(alpha = 0.11f),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MiniAppsDesignTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(app.description, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 2)
                }
                StatusBadge(
                    text = app.status.label,
                    container = accent.copy(alpha = 0.3f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.65f))
                ) {
                    Text("Öffnen")
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorit",
                        tint = if (favorite) Color(0xFFFFD76E) else Color.White
                    )
                }
                IconButton(onClick = onLongPress) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Mehr", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    container: Color
) {
    Surface(
        shape = RoundedCornerShape(100),
        color = container
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

enum class MiniAppStatus(val label: String) {
    NEW("Neu"),
    STABLE("Stabil"),
    BETA("Beta")
}

enum class MiniApp(
    val displayName: String,
    val emoji: String,
    val description: String,
    val category: MiniAppCategory,
    val status: MiniAppStatus
) {
    PROMPT_LAB("Prompt Lab", "🧪", "Prompts bauen, speichern, wiederverwenden", MiniAppCategory.PRODUCTIVITY, MiniAppStatus.NEW),
    VOICE_NOTES_AI("Voice Notes AI", "🎙️", "Spracheingaben transkribieren und verdichten", MiniAppCategory.PRODUCTIVITY, MiniAppStatus.NEW),
    SMART_WORKSPACE("Smart Workspace", "🧠", "Notizen strukturieren, zusammenfassen, ToDos extrahieren", MiniAppCategory.PRODUCTIVITY, MiniAppStatus.NEW),
    BROWSER("Mini-Browser", "🌐", "Im Web surfen", MiniAppCategory.UTILITY, MiniAppStatus.STABLE),
    PHOTO_STUDIO("Photo Studio", "🖼️", "Bilder verbessern: Filter, Crop, Rotation, Export", MiniAppCategory.CREATIVE, MiniAppStatus.NEW),
    DOODLE("Doodle-Pad", "🎨", "Zeichnen & Skizzieren", MiniAppCategory.CREATIVE, MiniAppStatus.STABLE),
    GAME_2048("2048", "🎮", "Klassisches Zahlen-Spiel", MiniAppCategory.CREATIVE, MiniAppStatus.STABLE),
    NOTES("Notizen", "📝", "Schnelle Notizen", MiniAppCategory.PRODUCTIVITY, MiniAppStatus.STABLE),
    AUTOMATION("Automation Board", "⚙️", "Schnellaktionen für Produktivität", MiniAppCategory.PRODUCTIVITY, MiniAppStatus.STABLE),
    KNOWLEDGE("Knowledge Vault", "📚", "Lokale Wissensbasis durchsuchen", MiniAppCategory.KNOWLEDGE, MiniAppStatus.BETA)
}

private enum class MiniAppsFilter(val label: String) {
    ALL("Alle"),
    FAVORITES("Favoriten"),
    PRODUCTIVITY("Produktiv"),
    CREATIVE("Kreativ"),
    UTILITY("Utility"),
    KNOWLEDGE("Knowledge")
}

private object MiniAppsDesignTokens {
    val cardRadius = RoundedCornerShape(20.dp)
    val surfaceRadius = RoundedCornerShape(16.dp)
    val heroRadius = RoundedCornerShape(24.dp)
    val cardPadding = 14.dp
}

private data class MiniAppMood(
    val top: Color,
    val bottom: Color,
    val appBar: Color,
    val card: Color
)

private const val MINI_APPS_PREFS_NAME = "mini_apps_v2"
private const val KEY_FAVORITE_APPS = "favorite_apps"
private const val KEY_HIDDEN_APPS = "hidden_apps"
private const val KEY_APP_ORDER = "app_order"
private const val KEY_LAST_USED = "last_used"

private fun miniAppMood(app: MiniApp?): MiniAppMood = when (app) {
    MiniApp.PROMPT_LAB -> MiniAppMood(
        top = Color(0xFF111E36),
        bottom = Color(0xFF1A315A),
        appBar = Color(0xFF3F68C3),
        card = Color(0xFF203E70)
    )
    MiniApp.VOICE_NOTES_AI -> MiniAppMood(
        top = Color(0xFF1F1B3A),
        bottom = Color(0xFF2F2458),
        appBar = Color(0xFF5E4BB9),
        card = Color(0xFF3E3382)
    )
    MiniApp.SMART_WORKSPACE -> MiniAppMood(
        top = Color(0xFF11262D),
        bottom = Color(0xFF17414E),
        appBar = Color(0xFF2D94A9),
        card = Color(0xFF296375)
    )
    MiniApp.BROWSER -> MiniAppMood(
        top = Color(0xFF0D2430),
        bottom = Color(0xFF113446),
        appBar = Color(0xFF1C5A73),
        card = Color(0xFF1A3D4F)
    )
    MiniApp.PHOTO_STUDIO -> MiniAppMood(
        top = Color(0xFF1A1E2F),
        bottom = Color(0xFF28334F),
        appBar = Color(0xFF5173D6),
        card = Color(0xFF324A85)
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
    MiniApp.AUTOMATION -> MiniAppMood(
        top = Color(0xFF1E1933),
        bottom = Color(0xFF2A2450),
        appBar = Color(0xFF6655C9),
        card = Color(0xFF38306A)
    )
    MiniApp.KNOWLEDGE -> MiniAppMood(
        top = Color(0xFF1B262B),
        bottom = Color(0xFF20363A),
        appBar = Color(0xFF2C8E93),
        card = Color(0xFF26535A)
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
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = remember { context.getSharedPreferences(MINI_APPS_PREFS_NAME, Context.MODE_PRIVATE) }
    var currentApp by remember { mutableStateOf<MiniApp?>(null) }
    var favorites by remember {
        mutableStateOf(
            loadStringSet(
                prefs = prefs,
                key = KEY_FAVORITE_APPS,
                fallback = setOf(MiniApp.PROMPT_LAB.name, MiniApp.NOTES.name, MiniApp.SMART_WORKSPACE.name)
            )
        )
    }
    var hiddenApps by remember { mutableStateOf(loadStringSet(prefs, KEY_HIDDEN_APPS, emptySet())) }
    var appOrder by remember { mutableStateOf(loadAppOrder(prefs)) }
    var lastUsedByApp by remember { mutableStateOf(loadLastUsedMap(prefs)) }
    var loadingHub by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(280)
        loadingHub = false
    }

    val mood = miniAppMood(currentApp)
    val backgroundGradient = Brush.verticalGradient(colors = listOf(mood.top, mood.bottom))

    fun showStatus(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
        }
    }

    fun openMiniApp(app: MiniApp) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentApp = app
        val updated = lastUsedByApp.toMutableMap().apply {
            put(app.name, System.currentTimeMillis())
        }
        lastUsedByApp = updated
        saveLastUsedMap(prefs, updated)
    }

    fun toggleFavorite(app: MiniApp) {
        val updated = favorites.toMutableSet().apply {
            if (contains(app.name)) remove(app.name) else add(app.name)
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        favorites = updated
        saveStringSet(prefs, KEY_FAVORITE_APPS, updated)
        showStatus(
            if (updated.contains(app.name)) {
                "${app.displayName} zu Favoriten hinzugefügt"
            } else {
                "${app.displayName} aus Favoriten entfernt"
            }
        )
    }

    fun toggleHidden(app: MiniApp) {
        val updated = hiddenApps.toMutableSet().apply {
            if (contains(app.name)) remove(app.name) else add(app.name)
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        hiddenApps = updated
        saveStringSet(prefs, KEY_HIDDEN_APPS, updated)
        showStatus(
            if (updated.contains(app.name)) {
                "${app.displayName} ausgeblendet"
            } else {
                "${app.displayName} wieder eingeblendet"
            }
        )
    }

    fun moveApp(app: MiniApp, direction: Int) {
        val ordered = deriveOrderedApps(appOrder).toMutableList()
        val index = ordered.indexOf(app)
        if (index < 0) return
        val targetIndex = (index + direction).coerceIn(0, ordered.lastIndex)
        if (index == targetIndex) return
        ordered.removeAt(index)
        ordered.add(targetIndex, app)
        val updatedOrder = ordered.map { it.name }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        appOrder = updatedOrder
        saveAppOrder(prefs, updatedOrder)
        showStatus("${app.displayName} neu angeordnet")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentApp?.let { "${it.emoji} ${it.displayName}" } ?: "🎯 Mini-Apps Discover",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (currentApp != null) currentApp = null else onClose() }) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundGradient)
        ) {
            when (currentApp) {
                null -> AppsHub(
                    themeColor = if (themeColor == Color.Unspecified) mood.appBar else themeColor,
                    cardColor = mood.card,
                    isLoading = loadingHub,
                    favorites = favorites,
                    hiddenApps = hiddenApps,
                    appOrder = appOrder,
                    lastUsedByApp = lastUsedByApp,
                    onOpenApp = ::openMiniApp,
                    onToggleFavorite = ::toggleFavorite,
                    onToggleHidden = ::toggleHidden,
                    onMoveApp = ::moveApp
                )
                MiniApp.PROMPT_LAB -> PromptLabApp(mood.appBar)
                MiniApp.VOICE_NOTES_AI -> VoiceNotesAiApp(mood.appBar)
                MiniApp.SMART_WORKSPACE -> SmartWorkspaceApp(mood.appBar)
                MiniApp.BROWSER -> MiniBrowser(mood.appBar)
                MiniApp.PHOTO_STUDIO -> PhotoStudioApp(mood.appBar)
                MiniApp.DOODLE -> DoodlePad(mood.appBar)
                MiniApp.GAME_2048 -> Game2048(mood.appBar)
                MiniApp.NOTES -> NotesApp(mood.appBar)
                MiniApp.AUTOMATION -> AutomationBoard(mood.appBar)
                MiniApp.KNOWLEDGE -> KnowledgeVault(mood.appBar)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsHub(
    themeColor: Color,
    cardColor: Color,
    isLoading: Boolean,
    favorites: Set<String>,
    hiddenApps: Set<String>,
    appOrder: List<String>,
    lastUsedByApp: Map<String, Long>,
    onOpenApp: (MiniApp) -> Unit,
    onToggleFavorite: (MiniApp) -> Unit,
    onToggleHidden: (MiniApp) -> Unit,
    onMoveApp: (MiniApp, Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(MiniAppsFilter.ALL) }
    var selectedAppForSheet by remember { mutableStateOf<MiniApp?>(null) }

    val orderedApps = remember(appOrder) { deriveOrderedApps(appOrder) }
    val visibleApps = remember(orderedApps, hiddenApps) {
        orderedApps.filterNot { hiddenApps.contains(it.name) }
    }
    val normalizedQuery = query.trim().lowercase()
    val filteredApps = remember(visibleApps, normalizedQuery, selectedFilter, favorites) {
        visibleApps.filter { app ->
            val categoryMatch = when (selectedFilter) {
                MiniAppsFilter.ALL -> true
                MiniAppsFilter.FAVORITES -> favorites.contains(app.name)
                MiniAppsFilter.PRODUCTIVITY -> app.category == MiniAppCategory.PRODUCTIVITY
                MiniAppsFilter.CREATIVE -> app.category == MiniAppCategory.CREATIVE
                MiniAppsFilter.UTILITY -> app.category == MiniAppCategory.UTILITY
                MiniAppsFilter.KNOWLEDGE -> app.category == MiniAppCategory.KNOWLEDGE
            }
            val searchMatch = normalizedQuery.isBlank() ||
                app.displayName.lowercase().contains(normalizedQuery) ||
                app.description.lowercase().contains(normalizedQuery)
            categoryMatch && searchMatch
        }
    }
    val recentApps = remember(visibleApps, lastUsedByApp) {
        visibleApps
            .filter { (lastUsedByApp[it.name] ?: 0L) > 0L }
            .sortedByDescending { lastUsedByApp[it.name] ?: 0L }
            .take(6)
    }
    val recommendedApps = remember(visibleApps, favorites, lastUsedByApp) {
        visibleApps
            .sortedWith(
                compareByDescending<MiniApp> { it.status == MiniAppStatus.NEW }
                    .thenByDescending { !favorites.contains(it.name) }
                    .thenBy { lastUsedByApp[it.name] ?: 0L }
            )
            .take(6)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 980.dp -> 3
            maxWidth >= 640.dp -> 2
            else -> 1
        }
        val rows = filteredApps.chunked(columns)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (columns > 1) 18.dp else 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MiniAppHeroCard() }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Mini-App suchen") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Suche löschen")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.72f)
                    )
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniAppsFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = themeColor.copy(alpha = 0.35f),
                                selectedLabelColor = Color.White,
                                containerColor = cardColor.copy(alpha = 0.5f),
                                labelColor = Color.White.copy(alpha = 0.9f)
                            )
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lade Mini-Apps ...", color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(3) {
                                MiniAppCardSkeleton(modifier = Modifier.width(200.dp))
                            }
                        }
                    }
                }
            } else {
                item {
                    AnimatedVisibility(
                        visible = recentApps.isNotEmpty(),
                        enter = fadeIn(animationSpec = tween(260)) + expandVertically(animationSpec = tween(260)),
                        exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniAppSectionTitle("Zuletzt genutzt")
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                recentApps.forEach { app ->
                                    MiniAppSpotlightCard(
                                        app = app,
                                        accent = themeColor,
                                        favorite = favorites.contains(app.name),
                                        onClick = { onOpenApp(app) },
                                        onLongPress = { selectedAppForSheet = app }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = recommendedApps.isNotEmpty(),
                        enter = fadeIn(animationSpec = tween(260)) + expandVertically(animationSpec = tween(260)),
                        exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniAppSectionTitle("Empfohlen")
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                recommendedApps.forEach { app ->
                                    MiniAppSpotlightCard(
                                        app = app,
                                        accent = themeColor,
                                        favorite = favorites.contains(app.name),
                                        onClick = { onOpenApp(app) },
                                        onLongPress = { selectedAppForSheet = app }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { MiniAppSectionTitle("Meine Apps") }

            if (rows.isEmpty()) {
                item {
                    MiniAppsEmptyState(
                        query = query,
                        filterLabel = selectedFilter.label,
                        onReset = {
                            query = ""
                            selectedFilter = MiniAppsFilter.ALL
                        }
                    )
                }
            } else {
                items(rows, key = { row -> row.joinToString(separator = "_") { it.name } }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            MiniAppLiveCard(
                                app = app,
                                accent = themeColor,
                                favorite = favorites.contains(app.name),
                                onOpen = { onOpenApp(app) },
                                onToggleFavorite = { onToggleFavorite(app) },
                                onLongPress = { selectedAppForSheet = app },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat((columns - row.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item { MiniAppSectionTitle("Schnellverwaltung") }
            if (visibleApps.isEmpty()) {
                item {
                    Surface(
                        shape = MiniAppsDesignTokens.surfaceRadius,
                        color = cardColor.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Alle Apps sind ausgeblendet. Öffne eine App über Bottom Sheet und blende sie wieder ein.",
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(visibleApps, key = { it.name }) { app ->
                    MiniAppManageSwipeRow(
                        app = app,
                        accent = themeColor,
                        favorite = favorites.contains(app.name),
                        hidden = hiddenApps.contains(app.name),
                        onOpen = { onOpenApp(app) },
                        onToggleFavorite = { onToggleFavorite(app) },
                        onToggleHidden = { onToggleHidden(app) },
                        onMoveUp = { onMoveApp(app, -1) },
                        onMoveDown = { onMoveApp(app, 1) }
                    )
                }
            }
        }
    }

    val appInSheet = selectedAppForSheet
    if (appInSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedAppForSheet = null },
            containerColor = Color(0xFF1B253A),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${appInSheet.emoji} ${appInSheet.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(appInSheet.description, color = Color.White.copy(alpha = 0.78f))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                CompactTextActionRow(
                    modifier = Modifier.fillMaxWidth(),
                    actions = listOf(
                        CompactTextAction(
                            label = "Öffnen",
                            onClick = {
                                selectedAppForSheet = null
                                onOpenApp(appInSheet)
                            }
                        ),
                        CompactTextAction(
                            label = if (favorites.contains(appInSheet.name)) "Favorit aus" else "Favorit an",
                            onClick = { onToggleFavorite(appInSheet) }
                        ),
                        CompactTextAction(
                            label = if (hiddenApps.contains(appInSheet.name)) "Einblenden" else "Ausblenden",
                            onClick = { onToggleHidden(appInSheet) }
                        )
                    )
                )
                CompactTextActionRow(
                    modifier = Modifier.fillMaxWidth(),
                    actions = listOf(
                        CompactTextAction(
                            label = "Nach oben",
                            onClick = { onMoveApp(appInSheet, -1) }
                        ),
                        CompactTextAction(
                            label = "Nach unten",
                            onClick = { onMoveApp(appInSheet, 1) }
                        )
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniAppManageSwipeRow(
    app: MiniApp,
    accent: Color,
    favorite: Boolean,
    hidden: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onToggleFavorite()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onToggleHidden()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val container = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF1E4A33).copy(alpha = 0.7f)
                SwipeToDismissBoxValue.EndToStart -> Color(0xFF5A2B2B).copy(alpha = 0.7f)
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(container, MiniAppsDesignTokens.surfaceRadius)
                    .padding(horizontal = 14.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Text("Favorit", color = Color.White, fontWeight = FontWeight.SemiBold)
                    SwipeToDismissBoxValue.EndToStart -> Text("Ausblenden", color = Color.White, fontWeight = FontWeight.SemiBold)
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        }
    ) {
        Surface(
            shape = MiniAppsDesignTokens.surfaceRadius,
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${app.emoji} ${app.displayName}", color = Color.White, modifier = Modifier.weight(1f))
                if (favorite) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD76E), modifier = Modifier.size(16.dp))
                }
                if (hidden) {
                    Icon(Icons.Default.VisibilityOff, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nach oben", tint = Color.White)
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nach unten", tint = Color.White)
                }
                IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Öffnen", tint = accent)
                }
            }
        }
    }
}

private fun deriveOrderedApps(savedOrder: List<String>): List<MiniApp> {
    return sanitizeAppOrder(savedOrder).mapNotNull { id ->
        MiniApp.entries.find { it.name == id }
    }
}

private fun sanitizeAppOrder(rawOrder: List<String>): List<String> {
    val validNames = MiniApp.entries.map { it.name }
    val seen = mutableSetOf<String>()
    val normalized = rawOrder.filter { name ->
        validNames.contains(name) && seen.add(name)
    }
    if (normalized.size == validNames.size) return normalized
    return normalized + validNames.filterNot { seen.contains(it) }
}

private fun loadStringSet(
    prefs: SharedPreferences,
    key: String,
    fallback: Set<String>
): Set<String> {
    return prefs.getStringSet(key, null)?.toSet() ?: fallback
}

private fun saveStringSet(prefs: SharedPreferences, key: String, value: Set<String>) {
    prefs.edit().putStringSet(key, value.toSet()).apply()
}

private fun loadAppOrder(prefs: SharedPreferences): List<String> {
    val raw = prefs.getString(KEY_APP_ORDER, "").orEmpty()
    if (raw.isBlank()) return sanitizeAppOrder(MiniApp.entries.map { it.name })
    val parsed = runCatching {
        Gson().fromJson(raw, Array<String>::class.java)?.toList().orEmpty()
    }.getOrElse { MiniApp.entries.map { it.name } }
    return sanitizeAppOrder(parsed)
}

private fun saveAppOrder(prefs: SharedPreferences, order: List<String>) {
    prefs.edit().putString(KEY_APP_ORDER, Gson().toJson(sanitizeAppOrder(order))).apply()
}

private fun loadLastUsedMap(prefs: SharedPreferences): Map<String, Long> {
    val raw = prefs.getString(KEY_LAST_USED, "").orEmpty()
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        Gson().fromJson(raw, Map::class.java)
            ?.mapNotNull { (k, v) ->
                val key = k?.toString().orEmpty()
                val value = (v as? Number)?.toLong() ?: 0L
                if (key.isBlank()) null else key to value
            }
            ?.toMap()
            .orEmpty()
    }.getOrDefault(emptyMap())
}

private fun saveLastUsedMap(prefs: SharedPreferences, value: Map<String, Long>) {
    prefs.edit().putString(KEY_LAST_USED, Gson().toJson(value)).apply()
}

private fun cleanupWebView(view: WebView) {
    runCatching {
        view.stopLoading()
        view.loadUrl("about:blank")
        view.clearHistory()
        view.clearCache(true)
        view.removeAllViews()
        view.destroy()
    }
}

// ===== Mini-Browser =====
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiniBrowser(themeColor: Color) {
    var browserUrl by rememberSaveable { mutableStateOf("https://www.google.com") }
    var inputUrl by rememberSaveable { mutableStateOf(browserUrl) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { cleanupWebView(it) }
            webView = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // URL Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1A1C1E),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (canGoBack) webView?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                    }
                    IconButton(
                        onClick = { webView?.reload() },
                        enabled = webView != null
                    ) {
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
                            browserUrl = target
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
                            canGoBack = view?.canGoBack() == true
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = view?.canGoBack() == true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            pageTitle = title ?: ""
                        }
                    }
                    loadUrl(browserUrl)
                    webView = this
                }
            },
            update = { view ->
                canGoBack = view.canGoBack()
                if (view.url != browserUrl) {
                    view.loadUrl(browserUrl)
                }
            }
        )
    }
}

// Photo Studio wurde nach PhotoStudioComponent.kt ausgelagert

// ===== Doodle-Pad =====
@Composable
private fun DoodlePad(themeColor: Color) {
    val paths = remember { mutableStateListOf<DoodlePath>() }
    var currentPath by remember { mutableStateOf<DoodlePath?>(null) }
    var canvasTick by remember { mutableIntStateOf(0) }
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
                        Icon(Icons.AutoMirrored.Filled.Undo, "Rückgängig", tint = Color.White)
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
                            canvasTick++
                        },
                        onDrag = { change, _ ->
                            val activePath = currentPath ?: return@detectDragGestures
                            val lastPoint = activePath.points.lastOrNull()
                            val nextPoint = change.position
                            val shouldAppend = if (lastPoint == null) {
                                true
                            } else {
                                val dx = nextPoint.x - lastPoint.x
                                val dy = nextPoint.y - lastPoint.y
                                (dx * dx + dy * dy) >= 4f
                            }
                            if (shouldAppend) {
                                activePath.points.add(nextPoint)
                                canvasTick++
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            currentPath?.let {
                                if (it.points.size > 1) {
                                    paths.add(it)
                                }
                            }
                            currentPath = null
                            canvasTick++
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE")
                val ignored = canvasTick
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
                    var totalDx = 0f
                    var totalDy = 0f
                    var swipeHandled = false
                    detectDragGestures(
                        onDragStart = {
                            totalDx = 0f
                            totalDy = 0f
                            swipeHandled = false
                        },
                        onDrag = { change, drag ->
                            if (gameOver.value || swipeHandled) return@detectDragGestures
                            totalDx += drag.x
                            totalDy += drag.y
                            if (abs(totalDx) < 34f && abs(totalDy) < 34f) return@detectDragGestures

                            swipeHandled = true
                            val direction = when {
                                abs(totalDx) > abs(totalDy) && totalDx > 0 -> Direction.RIGHT
                                abs(totalDx) > abs(totalDy) && totalDx < 0 -> Direction.LEFT
                                totalDy > 0 -> Direction.DOWN
                                else -> Direction.UP
                            }
                            val (newGrid, gainedScore) = move2048(grid.value, direction)
                            if (!sameGrid(grid.value, newGrid)) {
                                grid.value = spawn2048(newGrid)
                                score += gainedScore
                                if (newGrid.any { row -> row.any { it >= 2048 } }) won.value = true
                                if (isGameOver(grid.value)) gameOver.value = true
                            }
                            change.consume()
                        }
                    )
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

@Composable
private fun AutomationBoard(themeColor: Color) {
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var selectedId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Automation-Templates",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Kopiere einen Prompt und nutze ihn direkt im Chat oder Collab.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp
            )
        }
        items(AutomationCatalog.templates, key = { it.id }) { template ->
            val selected = selectedId == template.id
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) themeColor.copy(alpha = 0.35f) else Color(0xFF1F2431)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(template.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(template.description, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(template.prompt, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp)
                    CompactTextActionRow(
                        actions = listOf(
                            CompactTextAction(
                                label = "Prompt kopieren",
                                onClick = {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(template.prompt))
                                    selectedId = template.id
                                }
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeVault(themeColor: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { ChatRepository(ChatDatabase.getDatabase(context).chatDao()) }
    val notesPrefs = remember { context.getSharedPreferences("notes", Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Bereit") }
    var results by remember { mutableStateOf<List<com.example.bamachat.data.local.KnowledgeChunkEntity>>(emptyList()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Knowledge Vault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Importiere Notizen in die lokale Wissensbasis und suche sie später wieder.",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    scope.launch {
                        val notes = loadNotes(notesPrefs)
                        notes.forEach { note ->
                            repo.saveKnowledgeChunk(
                                sourceTitle = "MiniApp Notiz",
                                content = note.second,
                                keywords = "notiz,miniapp",
                                sourceType = "note"
                            )
                        }
                        status = "Importiert: ${notes.size} Notizen"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) {
                Text("Notizen importieren")
            }
            Button(
                onClick = {
                    scope.launch {
                        val token = query.trim()
                        if (token.isBlank()) {
                            results = emptyList()
                            status = "Suchbegriff fehlt"
                        } else {
                            results = repo.searchKnowledge(token, limit = 20)
                            status = "Treffer: ${results.size}"
                        }
                    }
                }
            ) {
                Text("Suchen")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Suchbegriff") },
            singleLine = true
        )
        Text(status, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.id }) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F2431)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.sourceTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(item.content.take(280), color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                        Text(
                            "Keywords: ${item.keywords}",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

private data class PromptLabTemplate(
    val id: String,
    val title: String,
    val prompt: String,
    val createdAt: Long
)

private data class VoiceNoteEntry(
    val id: String,
    val text: String,
    val createdAt: Long
)

@Composable
private fun PromptLabApp(themeColor: Color) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("mini_prompt_lab", Context.MODE_PRIVATE) }
    var role by rememberSaveable { mutableStateOf("Senior Software Architect") }
    var tone by rememberSaveable { mutableStateOf("Präzise, direkt, lösungsorientiert") }
    var outputFormat by rememberSaveable { mutableStateOf("Schritt-für-Schritt mit Risiken und Alternativen") }
    var contextText by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var constraints by rememberSaveable { mutableStateOf("Nutze klare Annahmen und zeige Trade-offs.") }
    var saveTitle by rememberSaveable { mutableStateOf("") }
    var savedTemplates by remember { mutableStateOf(loadPromptLabTemplates(prefs)) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bannerMessage) {
        if (!bannerMessage.isNullOrBlank()) {
            delay(1700)
            bannerMessage = null
        }
    }

    val generatedPrompt = remember(role, tone, outputFormat, contextText, goal, constraints) {
        buildString {
            appendLine("Rolle: $role")
            appendLine("Ton: $tone")
            appendLine("Ziel: ${goal.ifBlank { "Bitte Ziel ergänzen." }}")
            if (contextText.isNotBlank()) {
                appendLine("Kontext:")
                appendLine(contextText.trim())
            }
            appendLine("Ausgabeformat: $outputFormat")
            appendLine("Regeln: $constraints")
        }.trim()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Prompt Lab", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                "Baue wiederverwendbare High-Quality-Prompts für Personas und Agenten.",
                color = Color.White.copy(alpha = 0.74f),
                fontSize = 12.sp
            )
        }
        item {
            AnimatedVisibility(
                visible = !bannerMessage.isNullOrBlank(),
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(170)) + shrinkVertically(animationSpec = tween(170))
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2A3E5C).copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        bannerMessage.orEmpty(),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Rolle") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = tone,
                onValueChange = { tone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ton") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ziel / Task") }
            )
        }
        item {
            OutlinedTextField(
                value = contextText,
                onValueChange = { contextText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kontext") },
                minLines = 3
            )
        }
        item {
            OutlinedTextField(
                value = outputFormat,
                onValueChange = { outputFormat = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ausgabeformat") }
            )
        }
        item {
            OutlinedTextField(
                value = constraints,
                onValueChange = { constraints = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Regeln / Constraints") }
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1F2431),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Generierter Prompt", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(generatedPrompt, color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp)
                    CompactTextActionRow(
                        modifier = Modifier.fillMaxWidth(),
                        actions = listOf(
                            CompactTextAction(
                                label = "Kopieren",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(generatedPrompt))
                                    bannerMessage = "Prompt kopiert."
                                }
                            ),
                            CompactTextAction(
                                label = "Speichern",
                                onClick = {
                                    val template = PromptLabTemplate(
                                        id = System.currentTimeMillis().toString(),
                                        title = saveTitle.ifBlank { "Prompt ${savedTemplates.size + 1}" },
                                        prompt = generatedPrompt,
                                        createdAt = System.currentTimeMillis()
                                    )
                                    savedTemplates = listOf(template) + savedTemplates.take(24)
                                    savePromptLabTemplates(prefs, savedTemplates)
                                    saveTitle = ""
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    bannerMessage = "Prompt gespeichert."
                                }
                            )
                        )
                    )
                    OutlinedTextField(
                        value = saveTitle,
                        onValueChange = { saveTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Titel (optional)") },
                        singleLine = true
                    )
                }
            }
        }
        if (savedTemplates.isNotEmpty()) {
            item {
                Text("Gespeicherte Prompts", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            items(savedTemplates, key = { it.id }) { template ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1A202D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(template.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(template.prompt.take(420), color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Kopieren",
                                    onClick = {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(template.prompt))
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        bannerMessage = "Template kopiert."
                                    }
                                ),
                                CompactTextAction(
                                    label = "Laden",
                                    onClick = {
                                        role = extractLineValue(template.prompt, "Rolle:")
                                        tone = extractLineValue(template.prompt, "Ton:")
                                        outputFormat = extractLineValue(template.prompt, "Ausgabeformat:")
                                        constraints = extractLineValue(template.prompt, "Regeln:")
                                        goal = extractLineValue(template.prompt, "Ziel:")
                                        contextText = extractBlockAfterHeader(template.prompt, "Kontext:")
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        bannerMessage = "Template geladen."
                                    }
                                ),
                                CompactTextAction(
                                    label = "Löschen",
                                    onClick = {
                                        savedTemplates = savedTemplates.filterNot { it.id == template.id }
                                        savePromptLabTemplates(prefs, savedTemplates)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        bannerMessage = "Template gelöscht."
                                    }
                                )
                            )
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Noch keine gespeicherten Prompts. Erstelle einen ersten Prompt und speichere ihn.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

private fun loadPromptLabTemplates(prefs: SharedPreferences): List<PromptLabTemplate> {
    val raw = prefs.getString("templates_json", "[]").orEmpty()
    return runCatching {
        Gson().fromJson(raw, Array<PromptLabTemplate>::class.java)?.toList().orEmpty()
    }.getOrDefault(emptyList())
}

private fun savePromptLabTemplates(prefs: SharedPreferences, list: List<PromptLabTemplate>) {
    prefs.edit().putString("templates_json", Gson().toJson(list)).apply()
}

@Composable
private fun VoiceNotesAiApp(themeColor: Color) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("mini_voice_notes", Context.MODE_PRIVATE) }
    var notes by remember { mutableStateOf(loadVoiceNotes(prefs)) }
    var manualInput by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("Bereit") }
    var isListening by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode != Activity.RESULT_OK) {
            status = "Spracherkennung abgebrochen."
            return@rememberLauncherForActivityResult
        }
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        if (spokenText.isBlank()) {
            status = "Keine Sprache erkannt."
            return@rememberLauncherForActivityResult
        }
        notes = listOf(
            VoiceNoteEntry(
                id = System.currentTimeMillis().toString(),
                text = spokenText,
                createdAt = System.currentTimeMillis()
            )
        ) + notes
        saveVoiceNotes(prefs, notes)
        status = "Sprachnotiz gespeichert."
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val fullText = remember(notes) { notes.joinToString("\n") { it.text } }
    val summary = remember(fullText) { summarizeVoiceNotes(fullText) }
    val actionItems = remember(fullText) { extractActionItems(fullText) }
    val statusTone = remember(status) {
        val lower = status.lowercase()
        when {
            lower.contains("nicht") || lower.contains("fehlt") || lower.contains("abgebrochen") ->
                MiniAppStatusTone.ERROR
            lower.contains("gespeichert") || lower.contains("geleert") ->
                MiniAppStatusTone.SUCCESS
            else -> MiniAppStatusTone.INFO
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Voice Notes AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                "Erfasse Sprache und generiere automatisch Kernpunkte + nächste Schritte.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        runCatching {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich deine Notiz ein")
                            }
                            isListening = true
                            speechLauncher.launch(intent)
                            status = "Spracherkennung läuft ..."
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }.onFailure {
                            isListening = false
                            status = "Spracherkennung nicht verfügbar."
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) {
                    Text("Spracheingabe")
                }
                Button(
                    onClick = {
                        val text = manualInput.trim()
                        if (text.isBlank()) {
                            status = "Bitte Text eingeben."
                        } else {
                            notes = listOf(
                                VoiceNoteEntry(
                                    id = System.currentTimeMillis().toString(),
                                    text = text,
                                    createdAt = System.currentTimeMillis()
                                )
                            ) + notes
                            saveVoiceNotes(prefs, notes)
                            manualInput = ""
                            status = "Textnotiz gespeichert."
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Text speichern")
                }
            }
        }
        item {
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Alternative Texteingabe") },
                minLines = 2
            )
        }
        item {
            MiniAppStatusBanner(
                message = status,
                tone = statusTone,
                isLoading = isListening
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1F2431),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zusammenfassung", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(summary, color = Color.White.copy(alpha = 0.84f), fontSize = 12.sp)
                    Text("Nächste Schritte", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (actionItems.isEmpty()) {
                        Text("Noch keine eindeutigen ToDos erkannt.", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                    } else {
                        actionItems.forEach { item ->
                            Text("• $item", color = Color.White.copy(alpha = 0.84f), fontSize = 12.sp)
                        }
                    }
                    CompactTextActionRow(
                        actions = listOf(
                            CompactTextAction(
                                label = "Ergebnis kopieren",
                                onClick = {
                                    clipboard.setText(
                                        androidx.compose.ui.text.AnnotatedString(
                                            "Zusammenfassung:\n$summary\n\nToDos:\n${actionItems.joinToString("\n") { "- $it" }}"
                                        )
                                    )
                                }
                            ),
                            CompactTextAction(
                                label = "Alles löschen",
                                onClick = {
                                    notes = emptyList()
                                    saveVoiceNotes(prefs, notes)
                                    status = "Notizen geleert."
                                }
                            )
                        )
                    )
                }
            }
        }
        if (notes.isNotEmpty()) {
            item { Text("Transkripte", color = Color.White, fontWeight = FontWeight.SemiBold) }
            items(notes, key = { it.id }) { entry ->
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A202D), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.text, color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp)
                        CompactTextActionRow(
                            actions = listOf(
                                CompactTextAction(
                                    label = "Kopieren",
                                    onClick = {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(entry.text))
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                ),
                                CompactTextAction(
                                    label = "Löschen",
                                    onClick = {
                                        notes = notes.filterNot { it.id == entry.id }
                                        saveVoiceNotes(prefs, notes)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                )
                            )
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🎙️ Noch keine Transkripte", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Nutze Spracheingabe oder speichere manuellen Text, um hier Einträge zu sehen.",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartWorkspaceApp(themeColor: Color) {
    val haptics = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var sourceText by rememberSaveable { mutableStateOf("") }
    var outputText by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("Struktur") }
    var statusMessage by remember { mutableStateOf("Bereit. Füge Notizen ein und wähle einen Modus.") }
    var statusTone by remember { mutableStateOf(MiniAppStatusTone.INFO) }

    fun processInput(targetMode: String) {
        mode = targetMode
        if (sourceText.isBlank()) {
            outputText = ""
            statusMessage = "Bitte zuerst Rohnotizen eingeben."
            statusTone = MiniAppStatusTone.ERROR
            return
        }
        outputText = when (targetMode) {
            "Struktur" -> buildStructuredWorkspace(sourceText)
            "Summary" -> summarizeWorkspaceText(sourceText)
            else -> buildTodoWorkspace(sourceText)
        }
        statusMessage = when (targetMode) {
            "Struktur" -> "Struktur erstellt."
            "Summary" -> "Zusammenfassung erstellt."
            else -> "ToDos erstellt."
        }
        statusTone = MiniAppStatusTone.SUCCESS
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val dualPane = maxWidth >= 800.dp

        val content: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Input", color = Color.White, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    label = { Text("Rohtext / Notizen") }
                )
                Text(
                    "${sourceText.length} Zeichen • ${sourceText.lineSequence().count()} Zeilen",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp
                )
                MiniAppStatusBanner(
                    message = statusMessage,
                    tone = statusTone
                )
                AnimatedVisibility(visible = sourceText.isBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Füge erst Rohnotizen ein, dann nutze Struktur/Summary/ToDos.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { processInput("Struktur") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) { Text("Strukturieren") }
                    Button(onClick = { processInput("Summary") }, modifier = Modifier.weight(1f)) { Text("Zusammenfassen") }
                    Button(onClick = { processInput("Todo") }, modifier = Modifier.weight(1f)) { Text("ToDos") }
                }
            }
        }

        val output: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Output • $mode", color = Color.White, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A202D),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                ) {
                    Text(
                        outputText.ifBlank { "Noch keine Ausgabe. Wähle eine Aktion." },
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                CompactTextActionRow(
                    modifier = Modifier.fillMaxWidth(),
                    actions = listOf(
                        CompactTextAction(
                            label = "Output kopieren",
                            onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(outputText))
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                statusMessage = "Output kopiert."
                                statusTone = MiniAppStatusTone.SUCCESS
                            },
                            enabled = outputText.isNotBlank()
                        ),
                        CompactTextAction(
                            label = "Zurücksetzen",
                            onClick = {
                                sourceText = ""
                                outputText = ""
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                statusMessage = "Workspace zurückgesetzt."
                                statusTone = MiniAppStatusTone.INFO
                            }
                        )
                    )
                )
            }
        }

        if (dualPane) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) { content() }
                Box(modifier = Modifier.weight(1f)) { output() }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item { content() }
                item { output() }
            }
        }
    }
}

private fun loadVoiceNotes(prefs: SharedPreferences): List<VoiceNoteEntry> {
    val raw = prefs.getString("voice_notes_json", "[]").orEmpty()
    return runCatching {
        Gson().fromJson(raw, Array<VoiceNoteEntry>::class.java)?.toList().orEmpty()
    }.getOrDefault(emptyList())
}

private fun saveVoiceNotes(prefs: SharedPreferences, list: List<VoiceNoteEntry>) {
    prefs.edit().putString("voice_notes_json", Gson().toJson(list)).apply()
}

private fun summarizeVoiceNotes(text: String): String {
    val normalized = text.trim()
    if (normalized.isBlank()) return "Noch keine Inhalte."
    val chunks = normalized
        .split(Regex("[.!?\\n]"))
        .map { it.trim() }
        .filter { it.length > 4 }
        .take(5)
    if (chunks.isEmpty()) return normalized.take(240)
    return chunks.joinToString(separator = "\n") { "• $it" }
}

private fun extractActionItems(text: String): List<String> {
    val normalized = text.trim()
    if (normalized.isBlank()) return emptyList()
    val sentenceCandidates = normalized
        .split(Regex("[\\n.!?]"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val markerWords = listOf("todo", "muss", "soll", "nächste", "deadline", "bis", "prüfen", "bauen")
    val prioritized = sentenceCandidates
        .filter { sentence ->
            markerWords.any { marker -> sentence.lowercase().contains(marker) }
        }
        .take(6)
    return if (prioritized.isNotEmpty()) prioritized else sentenceCandidates.take(4)
}

private fun buildStructuredWorkspace(text: String): String {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) return "Keine Eingabe."
    return buildString {
        appendLine("Kontext")
        appendLine(lines.first())
        appendLine()
        appendLine("Kernpunkte")
        lines.drop(1).take(6).forEach { appendLine("- $it") }
        appendLine()
        appendLine("Offene Fragen")
        lines.takeLast(3).forEach { appendLine("- Was ist mit: $it ?") }
    }.trim()
}

private fun summarizeWorkspaceText(text: String): String {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) return "Keine Eingabe."
    return buildString {
        appendLine("Kurzfassung")
        appendLine(lines.take(1).joinToString(" "))
        appendLine()
        appendLine("Wesentliche Punkte")
        lines.drop(1).take(5).forEach { appendLine("• $it") }
    }.trim()
}

private fun buildTodoWorkspace(text: String): String {
    val items = extractActionItems(text)
    if (items.isEmpty()) return "Keine ToDos erkannt."
    return buildString {
        appendLine("ToDo Liste")
        items.forEachIndexed { index, task ->
            appendLine("${index + 1}. $task")
        }
    }.trim()
}

private fun extractLineValue(prompt: String, prefix: String): String {
    return prompt.lineSequence()
        .firstOrNull { it.trim().startsWith(prefix) }
        ?.substringAfter(prefix)
        ?.trim()
        .orEmpty()
}

private fun extractBlockAfterHeader(prompt: String, header: String): String {
    val lines = prompt.lines()
    val start = lines.indexOfFirst { it.trim() == header.trim() }
    if (start < 0) return ""
    val output = mutableListOf<String>()
    for (i in start + 1 until lines.size) {
        val line = lines[i]
        if (line.startsWith("Ausgabeformat:") || line.startsWith("Regeln:")) break
        output += line
    }
    return output.joinToString("\n").trim()
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
