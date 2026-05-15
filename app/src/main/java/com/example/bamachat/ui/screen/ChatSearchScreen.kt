package com.example.bamachat.ui.screen

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.MessageFtsResult
import com.example.bamachat.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
    onBack: () -> Unit,
    onOpenConversation: (conversationId: String) -> Unit
) {
    val app = LocalContext.current.applicationContext as? Application ?: return
    val repo = remember { ChatRepository(ChatDatabase.getDatabase(app).chatDao()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MessageFtsResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val textColor = Color(0xFFEDEEF0)
    val surfaceColor = Color(0xFF2A2D32)

    fun performSearch(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            results = emptyList()
            searched = false
            return
        }
        searchJob = scope.launch {
            loading = true
            delay(250)
            results = repo.searchMessages(q)
            searched = true
            loading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Chat-Suche", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; performSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Nachrichten durchsuchen...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = textColor.copy(alpha = 0.6f)) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = ""; results = emptyList(); searched = false }) {
                            Icon(Icons.Default.Clear, null, tint = textColor.copy(alpha = 0.6f))
                        }
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = textColor),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4F8CFF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                )
            )

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color(0xFF4F8CFF))
                    }
                }
                !searched -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Gib einen Suchbegriff ein", color = textColor.copy(alpha = 0.4f), fontSize = 14.sp)
                    }
                }
                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Keine Ergebnisse", color = textColor.copy(alpha = 0.4f), fontSize = 14.sp)
                    }
                }
                else -> {
                    Text("${results.size} Treffer", fontSize = 12.sp, color = textColor.copy(alpha = 0.6f))
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results, key = { it.rowid }) { result ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenConversation(result.conversation_id) },
                                shape = RoundedCornerShape(10.dp),
                                color = surfaceColor
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (result.is_user) "Du" else "KI",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (result.is_user) Color(0xFF4F8CFF) else Color(0xFF43C6AC)
                                        )
                                        Text(
                                            text = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                                                .format(Date(result.timestamp)),
                                            fontSize = 10.sp,
                                            color = textColor.copy(alpha = 0.4f)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = result.snippet.replace("<b>", "").replace("</b>", ""),
                                        fontSize = 13.sp,
                                        color = textColor,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
