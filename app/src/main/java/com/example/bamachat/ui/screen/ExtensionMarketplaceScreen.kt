package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GradientTop = Color(0xFF08111F)
private val GradientMid = Color(0xFF132844)
private val GradientBot = Color(0xFF18385E)
private val SurfaceColor = Color(0xFF213857).copy(alpha = 0.72f)
private val AccentColor = Color(0xFFB9CCFF)
private val TextSecondary = Color(0xFFD8E4FF)
private val CardBorder = Color(0xFF2F4A6E).copy(alpha = 0.6f)

private data class MarketplaceExtension(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val iconEmoji: String,
    val version: String,
    val downloads: Int,
    val rating: Float,
    val capabilities: List<String>,
    val category: String,
    val installed: Boolean = false
)

private val sampleExtensions = listOf(
    MarketplaceExtension(
        id = "web-scraper-pro", name = "Web Scraper Pro", description = "Erweiterte Web-Recherche mit DOM-Parsing und strukturierter Datenextraktion aus beliebigen Webseiten.",
        author = "Bama Labs", iconEmoji = "\uD83D\uDD0D", version = "2.1.0", downloads = 15420, rating = 4.7f,
        capabilities = listOf("LIVE_WEB", "FILE_IMPORT"), category = "Produktivität"
    ),
    MarketplaceExtension(
        id = "code-analyzer", name = "Code Analyzer", description = "Statische Code-Analyse für mehrere Sprachen mit Bug-Erkennung und Optimierungsvorschlägen.",
        author = "DevTools GmbH", iconEmoji = "\uD83D\uDCC8", version = "1.8.3", downloads = 23100, rating = 4.5f,
        capabilities = listOf("CHAT_READ", "FILE_IMPORT"), category = "Entwicklung"
    ),
    MarketplaceExtension(
        id = "meeting-assistant", name = "Meeting Assistant", description = "Automatische Meeting-Zusammenfassungen mit Action-Item-Erkennung und Terminplanung.",
        author = "Productivity AI", iconEmoji = "\uD83D\uDCC5", version = "3.0.1", downloads = 8720, rating = 4.3f,
        capabilities = listOf("CHAT_READ", "CHAT_WRITE"), category = "Produktivität"
    ),
    MarketplaceExtension(
        id = "social-media-poster", name = "Social Media Poster", description = "Erstelle und optimiere Beiträge für LinkedIn, Twitter und andere Plattformen.",
        author = "Content Creators", iconEmoji = "\uD83D\uDCF1", version = "1.2.0", downloads = 5600, rating = 4.1f,
        capabilities = listOf("CHAT_WRITE", "LIVE_WEB"), category = "Content"
    ),
    MarketplaceExtension(
        id = "data-visualizer", name = "Data Visualizer", description = "Verwandle Daten in interaktive Diagramme, Grafiken und aussagekräftige Visualisierungen.",
        author = "DataViz Inc", iconEmoji = "\uD83D\uDCCA", version = "2.4.0", downloads = 12340, rating = 4.6f,
        capabilities = listOf("FILE_IMPORT", "WORKSPACE_EDIT"), category = "Daten"
    ),
    MarketplaceExtension(
        id = "translation-hub", name = "Translation Hub", description = "Übersetzung in 50+ Sprachen mit Kontexterhalt und kultureller Anpassung.",
        author = "Global AI", iconEmoji = "\uD83C\uDF10", version = "1.5.2", downloads = 19800, rating = 4.8f,
        capabilities = listOf("CHAT_READ", "CHAT_WRITE"), category = "Content"
    ),
    MarketplaceExtension(
        id = "seo-optimizer", name = "SEO Optimizer", description = "SEO-Analyse für Webinhalte mit Keyword-Vorschlägen und Ranking-Optimierung.",
        author = "SEO Masters", iconEmoji = "\uD83D\uDD0D", version = "1.0.5", downloads = 4300, rating = 4.0f,
        capabilities = listOf("LIVE_WEB", "WORKSPACE_EDIT"), category = "Content"
    ),
    MarketplaceExtension(
        id = "pdf-pro", name = "PDF Pro", description = "Erweiterte PDF-Bearbeitung, Zusammenführung und Extraktion mit KI-gestützter Texterkennung.",
        author = "DocWorks", iconEmoji = "\uD83D\uDCC4", version = "2.0.0", downloads = 8950, rating = 4.4f,
        capabilities = listOf("FILE_IMPORT", "WORKSPACE_EDIT"), category = "Produktivität"
    )
)

private val allCategories = listOf("Alle", "Produktivität", "Entwicklung", "Content", "Daten")

@Composable
fun ExtensionMarketplaceScreen(onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Alle") }
    var extensions by remember { mutableStateOf(sampleExtensions) }

    val filtered = remember(searchQuery, selectedCategory, extensions) {
        extensions.filter { ext ->
            val matchesSearch = searchQuery.isBlank() ||
                ext.name.contains(searchQuery, ignoreCase = true) ||
                ext.description.contains(searchQuery, ignoreCase = true) ||
                ext.author.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "Alle" || ext.category == selectedCategory
            matchesSearch && matchesCategory
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
                    text = "Extension-Marktplatz",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Extensions durchsuchen...", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
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

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allCategories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                color = if (selectedCategory == category) Color.White else TextSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (selectedCategory == category) AccentColor.copy(alpha = 0.25f)
                            else SurfaceColor,
                            selectedContainerColor = AccentColor.copy(alpha = 0.25f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = CardBorder,
                            selectedBorderColor = AccentColor.copy(alpha = 0.5f),
                            enabled = true,
                            selected = selectedCategory == category
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = filtered, key = { e -> e.id }) { item ->
                    ExtensionMarketplaceCard(
                        ext = item,
                        onInstall = {
                            extensions = extensions.map { e ->
                                if (e.id == item.id) e.copy(installed = !e.installed) else e
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionMarketplaceCard(
    ext: MarketplaceExtension,
    onInstall: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(AccentColor.copy(alpha = 0.2f), AccentColor.copy(alpha = 0.08f))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = ext.iconEmoji, fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ext.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${ext.author} • v${ext.version}",
                        color = TextSecondary.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ext.installed) Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else AccentColor.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (!ext.installed) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (ext.installed) "Deinstallieren" else "Installieren",
                        fontSize = 11.sp,
                        color = if (ext.installed) Color(0xFF4CAF50) else AccentColor
                    )
                }
            }

            Text(
                text = ext.description,
                color = TextSecondary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ext.capabilities.forEach { cap ->
                        Surface(
                            shape = RoundedCornerShape(100),
                            color = AccentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = cap,
                                color = AccentColor.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { i ->
                        Icon(
                            imageVector = when {
                                i < ext.rating.toInt() -> Icons.Default.Star
                                i < ext.rating.toInt() + 1 && ext.rating % 1 >= 0.5f -> Icons.AutoMirrored.Filled.StarHalf
                                else -> Icons.Default.StarOutline
                            },
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = "%.1f".format(ext.rating),
                    color = TextSecondary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "\u2022",
                    color = TextSecondary.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.labelSmall
                )
                Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                Text(
                    text = formatDownloads(ext.downloads),
                    color = TextSecondary.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatDownloads(count: Int): String = when {
    count >= 1000 -> "%.1fk".format(count / 1000f)
    else -> count.toString()
}
