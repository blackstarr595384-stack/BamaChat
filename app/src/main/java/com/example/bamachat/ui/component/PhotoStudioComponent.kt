package com.example.bamachat.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bamachat.ui.screen.MiniAppStatusBanner
import com.example.bamachat.ui.screen.MiniAppStatusTone
import com.example.bamachat.util.PhotoAiAction
import com.example.bamachat.util.PhotoAiActionExecutor
import com.example.bamachat.util.PhotoAiAdjustments
import com.example.bamachat.util.PhotoAiExecutionStatus
import com.example.bamachat.util.PhotoAiPermissionSet
import com.example.bamachat.util.PhotoAiPolicyStore
import com.example.bamachat.util.PhotoAiRiskLevel
import com.example.bamachat.util.PhotoAiToolCatalog
import com.example.bamachat.util.PhotoAiToolId
import com.example.bamachat.util.buildPhotoAiPreviewColorMatrix
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PhotoCropPreset(val label: String, val ratio: Float) {
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    WIDE("16:9", 16f / 9f)
}

@Composable
fun PhotoStudioApp(themeColor: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var toolPermissions by remember { mutableStateOf(PhotoAiPolicyStore.load(context)) }
    val actionExecutor = remember(toolPermissions) { PhotoAiActionExecutor(context, toolPermissions) }
    var pendingConfirmAction by remember { mutableStateOf<PhotoAiAction?>(null) }
    var pendingConfirmInput by remember { mutableStateOf<Bitmap?>(null) }

    var history by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var historyIndex by remember { mutableIntStateOf(-1) }

    var brightness by rememberSaveable { mutableFloatStateOf(0f) }
    var contrast by rememberSaveable { mutableFloatStateOf(1f) }
    var saturation by rememberSaveable { mutableFloatStateOf(1f) }
    var warmth by rememberSaveable { mutableFloatStateOf(1f) }
    var showOriginalPreview by rememberSaveable { mutableStateOf(false) }
    var selectedCropPresetName by rememberSaveable { mutableStateOf(PhotoCropPreset.SQUARE.name) }
    var actionInProgress by remember { mutableStateOf(false) }
    var actionBannerMessage by remember { mutableStateOf("") }
    var actionBannerTone by remember { mutableStateOf(MiniAppStatusTone.INFO) }
    var actionRunningLabel by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = decodeBitmapForPhotoStudio(context, uri)
        if (bitmap == null) {
            scope.launch {
                snackbarHostState.showSnackbar("Bild konnte nicht geladen werden")
            }
            return@rememberLauncherForActivityResult
        }
        history = listOf(bitmap)
        historyIndex = 0
        brightness = 0f
        contrast = 1f
        saturation = 1f
        warmth = 1f
        showOriginalPreview = false
    }

    val activeBitmap = history.getOrNull(historyIndex)
    val selectedCropPreset = runCatching { PhotoCropPreset.valueOf(selectedCropPresetName) }
        .getOrDefault(PhotoCropPreset.SQUARE)
    val adjustments = PhotoAiAdjustments(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        warmth = warmth
    )
    val hasFilterChanges = brightness != 0f || contrast != 1f || saturation != 1f || warmth != 1f
    val activeMatrix = remember(adjustments) { buildPhotoAiPreviewColorMatrix(adjustments) }
    val controlsEnabled = !actionInProgress

    fun persistToolPermissions(next: PhotoAiPermissionSet) {
        toolPermissions = next
        PhotoAiPolicyStore.save(context, next)
    }

    fun resetAdjustments() {
        brightness = 0f
        contrast = 1f
        saturation = 1f
        warmth = 1f
    }

    fun pushHistory(bitmap: Bitmap) {
        val mutableBitmap = copyBitmapMutable(bitmap) ?: return
        val base = if (historyIndex in history.indices) {
            history.take(historyIndex + 1)
        } else {
            emptyList()
        }
        var updated = base + mutableBitmap
        if (updated.size > 6) {
            updated = updated.takeLast(6)
        }
        history = updated
        historyIndex = updated.lastIndex
    }

    suspend fun runPhotoAction(
        action: PhotoAiAction,
        input: Bitmap,
        confirmHighRisk: Boolean = false
    ) {
        val toolLabel = PhotoAiToolCatalog.findById(action.toolId)?.label ?: action.toolId.key
        actionInProgress = true
        actionBannerMessage = ""
        actionRunningLabel = "Verarbeite: $toolLabel"
        try {
            val result = actionExecutor.execute(
                action = action,
                input = input,
                confirmHighRisk = confirmHighRisk
            )
            when (result.status) {
                PhotoAiExecutionStatus.APPLIED -> {
                    val output = result.bitmap
                    if (output == null) {
                        actionBannerMessage = "Aktion abgeschlossen, aber ohne Bildausgabe."
                        actionBannerTone = MiniAppStatusTone.ERROR
                        snackbarHostState.showSnackbar(actionBannerMessage)
                        return
                    }
                    pushHistory(output)
                    if (action is PhotoAiAction.ApplyAdjustments || action == PhotoAiAction.AutoEnhance) {
                        resetAdjustments()
                        showOriginalPreview = false
                    }
                    if (result.message.isNotBlank()) {
                        actionBannerMessage = result.message
                        actionBannerTone = MiniAppStatusTone.SUCCESS
                        snackbarHostState.showSnackbar(result.message)
                    }
                }

                PhotoAiExecutionStatus.EXPORTED -> {
                    val exportText = result.exportUri?.toString().orEmpty()
                    val message = if (exportText.isBlank()) {
                        result.message.ifBlank { "Export abgeschlossen." }
                    } else {
                        "Gespeichert: $exportText"
                    }
                    actionBannerMessage = message
                    actionBannerTone = MiniAppStatusTone.SUCCESS
                    snackbarHostState.showSnackbar(message)
                }

                PhotoAiExecutionStatus.REQUIRES_CONFIRMATION -> {
                    pendingConfirmAction = action
                    pendingConfirmInput = copyBitmapMutable(input) ?: input
                    actionBannerMessage = "Bestätigung erforderlich für $toolLabel."
                    actionBannerTone = MiniAppStatusTone.INFO
                }

                PhotoAiExecutionStatus.BLOCKED,
                PhotoAiExecutionStatus.CLOUD_NOT_READY,
                PhotoAiExecutionStatus.FAILED -> {
                    actionBannerMessage = result.message
                    actionBannerTone = MiniAppStatusTone.ERROR
                    snackbarHostState.showSnackbar(result.message)
                }
            }
        } catch (error: Exception) {
            actionBannerMessage = error.message ?: "Aktion fehlgeschlagen."
            actionBannerTone = MiniAppStatusTone.ERROR
            snackbarHostState.showSnackbar(actionBannerMessage)
        } finally {
            actionInProgress = false
            actionRunningLabel = ""
        }
    }

    fun enqueuePhotoAction(
        action: PhotoAiAction,
        input: Bitmap,
        confirmHighRisk: Boolean = false
    ) {
        if (actionInProgress) {
            scope.launch { snackbarHostState.showSnackbar("Bitte warte, bis die aktuelle Aktion abgeschlossen ist.") }
            return
        }
        scope.launch {
            runPhotoAction(
                action = action,
                input = input,
                confirmHighRisk = confirmHighRisk
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.12f),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Photo Studio",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        TextButton(
                            onClick = {
                                imagePickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            enabled = controlsEnabled
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Bild wählen", color = Color.White)
                        }
                    }

                    PhotoAiPermissionsPanel(
                        permissions = toolPermissions,
                        onPermissionsChange = ::persistToolPermissions
                    )
                    AnimatedVisibility(
                        visible = actionInProgress || actionBannerMessage.isNotBlank(),
                        enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                        exit = fadeOut(animationSpec = tween(170)) + shrinkVertically(animationSpec = tween(170))
                    ) {
                        MiniAppStatusBanner(
                            message = if (actionInProgress) actionRunningLabel else actionBannerMessage,
                            tone = if (actionInProgress) MiniAppStatusTone.INFO else actionBannerTone,
                            isLoading = actionInProgress
                        )
                    }

                    if (activeBitmap == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                "Lade ein Foto und bearbeite es wie in einem Mini-Editor.",
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        adjustViewBounds = true
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    }
                                },
                                update = { imageView ->
                                    imageView.setImageBitmap(activeBitmap)
                                    if (showOriginalPreview) {
                                        imageView.clearColorFilter()
                                    } else {
                                        imageView.colorFilter = ColorMatrixColorFilter(activeMatrix)
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${activeBitmap.width}×${activeBitmap.height}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Vorher", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                Switch(
                                    checked = showOriginalPreview,
                                    onCheckedChange = { showOriginalPreview = it }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    enqueuePhotoAction(
                                        action = PhotoAiAction.Rotate(-90f),
                                        input = activeBitmap
                                    )
                                },
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateLeft, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Links")
                            }
                            FilledTonalButton(
                                onClick = {
                                    enqueuePhotoAction(
                                        action = PhotoAiAction.Rotate(90f),
                                        input = activeBitmap
                                    )
                                },
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Rechts")
                            }
                            FilledTonalButton(
                                onClick = {
                                    enqueuePhotoAction(
                                        action = PhotoAiAction.Mirror,
                                        input = activeBitmap
                                    )
                                },
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Flip, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Spiegeln")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhotoCropPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = selectedCropPreset == preset,
                                    onClick = { selectedCropPresetName = preset.name },
                                    label = { Text(preset.label) }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    enqueuePhotoAction(
                                        action = PhotoAiAction.Crop(selectedCropPreset.ratio),
                                        input = activeBitmap
                                    )
                                },
                                enabled = controlsEnabled
                            ) {
                                Text("Crop anwenden")
                            }
                        }

                        Text(
                            "AI Pro Tools",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    enqueuePhotoAction(
                                        action = PhotoAiAction.AutoEnhance,
                                        input = activeBitmap
                                    )
                                },
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Auto")
                            }
                            OutlinedButton(
                                onClick = {
                                    enqueuePhotoAction(
                                        action = PhotoAiAction.BackgroundRemove,
                                        input = activeBitmap
                                    )
                                },
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("BG Remove")
                            }
                            OutlinedButton(
                                onClick = {
                                    enqueuePhotoAction(
                                        action = PhotoAiAction.UpscaleHd,
                                        input = activeBitmap
                                    )
                                },
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Upscale")
                            }
                        }

                        PhotoStudioSlider(
                            label = "Helligkeit",
                            value = brightness,
                            valueRange = -0.35f..0.35f,
                            onValueChange = { brightness = it },
                            themeColor = themeColor
                        )
                        PhotoStudioSlider(
                            label = "Kontrast",
                            value = contrast,
                            valueRange = 0.7f..1.8f,
                            onValueChange = { contrast = it },
                            themeColor = themeColor
                        )
                        PhotoStudioSlider(
                            label = "Sättigung",
                            value = saturation,
                            valueRange = 0f..2f,
                            onValueChange = { saturation = it },
                            themeColor = themeColor
                        )
                        PhotoStudioSlider(
                            label = "Wärme",
                            value = warmth,
                            valueRange = 0.6f..1.5f,
                            onValueChange = { warmth = it },
                            themeColor = themeColor
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (historyIndex > 0) historyIndex--
                                },
                                enabled = historyIndex > 0 && controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Undo")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (historyIndex < history.lastIndex) historyIndex++
                                },
                                enabled = historyIndex >= 0 && historyIndex < history.lastIndex && controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Redo")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    enqueuePhotoAction(
                                        action = PhotoAiAction.ApplyAdjustments(adjustments),
                                        input = activeBitmap
                                    )
                                },
                                enabled = hasFilterChanges && controlsEnabled,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                            ) {
                                Text("Filter anwenden", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = { resetAdjustments() },
                                enabled = hasFilterChanges && controlsEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset")
                            }
                        }

                        Button(
                            onClick = {
                                val exportSource = if (showOriginalPreview || !hasFilterChanges) {
                                    activeBitmap
                                } else {
                                    applyPreviewAdjustmentsForExport(activeBitmap, activeMatrix) ?: activeBitmap
                                }
                                enqueuePhotoAction(
                                    action = PhotoAiAction.ExportHd,
                                    input = exportSource,
                                    confirmHighRisk = true
                                )
                            },
                            enabled = controlsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor.copy(alpha = 0.92f))
                        ) {
                            Icon(Icons.Default.SaveAlt, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("In Galerie speichern", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    val pendingAction = pendingConfirmAction
    val pendingInput = pendingConfirmInput
    val pendingManifest = pendingAction?.let { PhotoAiToolCatalog.findById(it.toolId) }
    if (pendingAction != null && pendingInput != null) {
        AlertDialog(
            onDismissRequest = {
                pendingConfirmAction = null
                pendingConfirmInput = null
            },
            title = { Text("Tool-Bestatigung") },
            text = {
                Text(
                    "Tool '${pendingManifest?.label ?: pendingAction.toolId.key}' hat Risiko " +
                        "${pendingManifest?.risk?.name ?: "UNKNOWN"}. Mochtest du fortfahren?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingConfirmAction
                    val input = pendingConfirmInput
                    pendingConfirmAction = null
                    pendingConfirmInput = null
                    if (action != null && input != null) {
                        scope.launch {
                            runPhotoAction(
                                action = action,
                                input = input,
                                confirmHighRisk = true
                            )
                        }
                    }
                }) {
                    Text("Ausfuhren")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingConfirmAction = null
                    pendingConfirmInput = null
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun PhotoAiPermissionsPanel(
    permissions: PhotoAiPermissionSet,
    onPermissionsChange: (PhotoAiPermissionSet) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "AI-Rechte (Schritt 1)",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Definiert, welche lokalen/cloud AI-Tools genutzt werden durfen.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cloud-Tools erlauben", color = Color.White, fontSize = 12.sp)
                Switch(
                    checked = permissions.allowCloudTools,
                    onCheckedChange = { onPermissionsChange(permissions.copy(allowCloudTools = it)) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bestatigung bei Risiko HIGH", color = Color.White, fontSize = 12.sp)
                Switch(
                    checked = permissions.requireConfirmationForHighRisk,
                    onCheckedChange = {
                        onPermissionsChange(permissions.copy(requireConfirmationForHighRisk = it))
                    }
                )
            }

            PhotoAiToolCatalog.curated.forEach { tool ->
                val enabled = permissions.isToolEnabled(tool.id)
                val disabledByCloud = tool.isCloudTool && !permissions.allowCloudTools
                val riskColor = when (tool.risk) {
                    PhotoAiRiskLevel.LOW -> Color(0xFF49C27D)
                    PhotoAiRiskLevel.MEDIUM -> Color(0xFFE8B24B)
                    PhotoAiRiskLevel.HIGH -> Color(0xFFEA6A6A)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tool.label + if (tool.isCloudTool) " (Cloud)" else " (Local)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            tool.description,
                            color = Color.White.copy(alpha = 0.67f),
                            fontSize = 10.sp
                        )
                    }
                    Surface(
                        color = riskColor.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            tool.risk.name,
                            color = riskColor,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            onPermissionsChange(permissions.withToolEnabled(tool.id, it))
                        },
                        enabled = !disabledByCloud || enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoStudioSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    themeColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 12.sp)
            Text(String.format("%.2f", value), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = themeColor,
                activeTrackColor = themeColor
            )
        )
    }
}

private fun decodeBitmapForPhotoStudio(
    context: Context,
    uri: Uri,
    maxSide: Int = 1600
): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        try {
            BitmapFactory.decodeStream(boundsStream, null, bounds)
        } finally {
            boundsStream.close()
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateSampleSizeForPhotoStudio(bounds.outWidth, bounds.outHeight, maxSide)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
        val decoded = try {
            BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
        } finally {
            decodeStream.close()
        } ?: return null

        copyBitmapMutable(decoded)
    }.getOrNull()
}

private fun calculateSampleSizeForPhotoStudio(width: Int, height: Int, maxSide: Int): Int {
    var inSampleSize = 1
    var halfWidth = width / 2
    var halfHeight = height / 2
    while (halfWidth / inSampleSize > maxSide || halfHeight / inSampleSize > maxSide) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
}

private fun copyBitmapMutable(bitmap: Bitmap): Bitmap? {
    val config = bitmap.config ?: Bitmap.Config.ARGB_8888
    return runCatching { bitmap.copy(config, true) }.getOrNull()
}

private fun applyPreviewAdjustmentsForExport(
    source: Bitmap,
    matrix: ColorMatrix
): Bitmap? {
    return runCatching {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        output
    }.getOrNull()
}
