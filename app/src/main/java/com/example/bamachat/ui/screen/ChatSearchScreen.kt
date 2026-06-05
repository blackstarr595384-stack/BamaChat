package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.viewmodel.SearchViewModel
import com.example.bamachat.ui.viewmodel.SearchFilter
import com.example.bamachat.ui.viewmodel.SearchSortBy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
    onBack: () -> Unit,
    onOpenConversation: (conversationId: String) -> Unit,
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val query by searchViewModel.query.collectAsStateWithLifecycle()
    val results by searchViewModel.results.collectAsStateWithLifecycle()
    val loading by searchViewModel.loading.collectAsStateWithLifecycle()
    val searched by searchViewModel.hasSearched.collectAsStateWithLifecycle()
    val filter by searchViewModel.selectedFilter.collectAsStateWithLifecycle()
    val totalResults by searchViewModel.totalResultsCount.collectAsStateWithLifecycle()
    val highlightedId by searchViewModel.highlightedResultId.collectAsStateWithLifecycle()

    var showFilterSheet by remember { mutableStateOf(false) }

    val textColor = Color(0xFFEDEEF0)
    val surfaceColor = Color(0xFF2A2D32)

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = surfaceColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Filter", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)

                // Message Type Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = filter.isUserMessagesOnly,
                        onCheckedChange = { searchViewModel.setUserMessagesOnly(it) },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4F8CFF))
                    )
                    Text("Nur deine Nachrichten", fontSize = 14.sp, color = textColor)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = filter.isAiMessagesOnly,
                        onCheckedChange = { searchViewModel.setAiMessagesOnly(it) },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4F8CFF))
                    )
                    Text("Nur KI-Antworten", fontSize = 14.sp, color = textColor)
                }

                HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.1f))

                // Sort Filter
                Text("Sortierung", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = textColor.copy(alpha = 0.8f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        SearchSortBy.entries.forEach { sortBy ->
                        FilterChip(
                            onClick = { searchViewModel.setSortBy(sortBy) },
                            label = { Text(sortBy.name) },
                            selected = filter.sortBy == sortBy
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Chat-Suche", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = textColor)
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = textColor)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
                .background(Color(0xFF1A1A2D))
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { searchViewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Nachrichten durchsuchen...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = textColor.copy(alpha = 0.6f)) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { searchViewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, null, tint = textColor.copy(alpha = 0.6f))
                        }
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = textColor),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4F8CFF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor
                )
            )

            // Active Filters Display
            if (filter.isUserMessagesOnly || filter.isAiMessagesOnly || filter.sortBy != SearchSortBy.RELEVANCE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (filter.isUserMessagesOnly) {
                        AssistChip(
                            onClick = { searchViewModel.setUserMessagesOnly(false) },
                            label = { Text("Deine", fontSize = 11.sp) }
                        )
                    }
                    if (filter.isAiMessagesOnly) {
                        AssistChip(
                            onClick = { searchViewModel.setAiMessagesOnly(false) },
                            label = { Text("KI", fontSize = 11.sp) }
                        )
                    }
                    if (filter.sortBy != SearchSortBy.RELEVANCE) {
                        AssistChip(
                            onClick = { searchViewModel.setSortBy(SearchSortBy.RELEVANCE) },
                            label = { Text(filter.sortBy.name, fontSize = 11.sp) }
                        )
                    }
                }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF4F8CFF)
                        )
                    }
                }
                !searched -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Gib einen Suchbegriff ein",
                            color = textColor.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Keine Treffer",
                                color = textColor.copy(alpha = 0.4f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Versuche eine andere Suchanfrage oder andere Filter.",
                                color = textColor.copy(alpha = 0.3f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        "$totalResults ${if (totalResults == 1) "Treffer" else "Treffer"}",
                        fontSize = 12.sp,
                        color = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results, key = { it.rowid }) { result ->
                            SearchResultCard(
                                result = result,
                                isHighlighted = highlightedId == result.rowid,
                                onHighlight = { searchViewModel.highlightResult(result.rowid) },
                                onOpen = { onOpenConversation(result.conversation_id) },
                                textColor = textColor,
                                surfaceColor = surfaceColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: com.example.bamachat.data.local.MessageFtsResult,
    isHighlighted: Boolean,
    onHighlight: () -> Unit,
    onOpen: () -> Unit,
    textColor: Color,
    surfaceColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onHighlight()
                onOpen()
            },
        shape = RoundedCornerShape(10.dp),
        color = if (isHighlighted) Color(0xFF4F8CFF).copy(alpha = 0.2f) else surfaceColor,
        border = if (isHighlighted) androidx.compose.foundation.BorderStroke(
            1.5.dp,
            Color(0xFF4F8CFF)
        ) else null
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
