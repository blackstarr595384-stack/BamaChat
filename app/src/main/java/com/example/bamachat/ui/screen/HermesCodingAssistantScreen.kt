package com.example.bamachat.ui.screen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.bamachat.ui.viewmodel.HERMES_CODING_MAX_IMPORT_BYTES
import com.example.bamachat.ui.viewmodel.HermesCodingAssistantMode
import com.example.bamachat.ui.viewmodel.HermesCodingAssistantViewModel
import java.io.ByteArrayOutputStream

private val ChatBackground = Color(0xFF050507)
private val ChatPanel = Color(0xFF101115)
private val ChatPanelSoft = Color(0xFF171922)
private val ChatBorder = Color(0xFF2A2D36)
private val ChatText = Color(0xFFF2F3F5)
private val ChatMuted = Color(0xFFA7ABB7)
private val ChatAccent = Color(0xFF7C5CFF)
private val ChatAssistantBubble = Color(0xFF151820)
private val ChatUserInput = Color(0xFF0E1016)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesCodingAssistantScreen(
    onBack: () -> Unit,
    viewModel: HermesCodingAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importTextDocument(
            context = context,
            uri = uri,
            onSuccess = viewModel::importTextFile,
            onError = viewModel::rejectImportedFile
        )
    }

    Scaffold(
        containerColor = ChatBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hermes Coding Assistant",
                        color = ChatText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = ChatText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChatBackground,
                    titleContentColor = ChatText,
                    navigationIconContentColor = ChatText
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.width(1.dp))

                uiState.importWarning?.let { warning ->
                    StatusBubble(
                        text = warning,
                        background = Color(0xFF17251E),
                        foreground = Color(0xFFC5F4D6)
                    )
                }

                uiState.error?.let { error ->
                    StatusBubble(
                        text = error,
                        background = Color(0xFF351719),
                        foreground = Color(0xFFFFD0D3)
                    )
                }

                AssistantBubble(
                    text = uiState.result ?: "Noch kein Review gestartet. Der Verlauf bleibt bewusst leer und fokussiert, bis du eine Analyse ausführst.",
                    isPlaceholder = uiState.result == null
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(26.dp),
                color = ChatPanel,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = 112.dp),
                        placeholder = {
                            Text(
                                text = "Nachricht oder Code eingeben …",
                                color = ChatMuted
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = ChatText,
                            fontFamily = FontFamily.Monospace
                        ),
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ChatText,
                            unfocusedTextColor = ChatText,
                            focusedContainerColor = ChatUserInput,
                            unfocusedContainerColor = ChatUserInput,
                            focusedBorderColor = ChatAccent,
                            unfocusedBorderColor = ChatBorder,
                            cursorColor = ChatAccent
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HermesCodingAssistantMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.selectedMode == mode,
                                onClick = { viewModel.selectMode(mode) },
                                label = { Text(mode.label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ChatPanelSoft,
                                    labelColor = ChatMuted,
                                    selectedContainerColor = ChatAccent,
                                    selectedLabelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = uiState.selectedMode == mode,
                                    borderColor = ChatBorder,
                                    selectedBorderColor = ChatAccent
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("text/*")) },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ChatText,
                                disabledContentColor = ChatMuted
                            )
                        ) {
                            Text("Import")
                        }
                        Button(
                            onClick = viewModel::analyze,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isLoading && uiState.input.isNotBlank(),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChatAccent,
                                contentColor = Color.White,
                                disabledContainerColor = ChatPanelSoft,
                                disabledContentColor = ChatMuted
                            )
                        ) {
                            Text(if (uiState.isLoading) "Analysiere…" else "Analysieren")
                        }
                    }

                    Text(
                        text = "Nur Textdateien. Mögliche Secrets werden vor der Analyse maskiert.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChatMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    isPlaceholder: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = ChatAssistantBubble,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Assistant",
                style = MaterialTheme.typography.labelLarge,
                color = ChatAccent,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = text,
                style = if (isPlaceholder) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                },
                color = if (isPlaceholder) ChatMuted else ChatText
            )
        }
    }
}

@Composable
private fun StatusBubble(
    text: String,
    background: Color,
    foreground: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 32.dp),
        shape = RoundedCornerShape(20.dp),
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = foreground
        )
    }
}

private fun importTextDocument(
    context: Context,
    uri: Uri,
    onSuccess: (String, String) -> Unit,
    onError: (String) -> Unit
) {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri).orEmpty()
    val fileName = queryDisplayName(context, uri) ?: "Textdatei"
    val fileSize = queryFileSize(context, uri)
    val extensionLooksText = fileName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase() in setOf("txt", "kt", "kts", "java", "xml", "json", "md", "gradle", "properties", "yml", "yaml", "csv", "log")
    if (!mimeType.startsWith("text/") && !extensionLooksText) {
        onError("Nur Textdateien können importiert werden.")
        return
    }
    if (fileSize != null && fileSize > HERMES_CODING_MAX_IMPORT_BYTES) {
        onError("Die Datei ist zu groß. Maximal erlaubt sind 200 KB.")
        return
    }

    runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > HERMES_CODING_MAX_IMPORT_BYTES) {
                    throw IllegalArgumentException("Die Datei ist zu groß. Maximal erlaubt sind 200 KB.")
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        } ?: throw IllegalArgumentException("Datei konnte nicht geöffnet werden.")
    }.onSuccess { text ->
        if (text.isBlank()) {
            onError("Die Textdatei enthält keinen lesbaren Inhalt.")
        } else {
            onSuccess(fileName, text)
        }
    }.onFailure { error ->
        onError(error.message ?: "Textdatei konnte nicht importiert werden.")
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }

private fun queryFileSize(context: Context, uri: Uri): Long? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
    }
