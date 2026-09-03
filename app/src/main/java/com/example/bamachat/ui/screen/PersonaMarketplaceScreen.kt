package com.example.bamachat.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.viewmodel.PersonaCategory
import com.example.bamachat.ui.viewmodel.PersonaListing
import com.example.bamachat.ui.viewmodel.PersonaMarketplaceViewModel

private val GradientTop = Color(0xFF08111F)
private val GradientMid = Color(0xFF132844)
private val GradientBot = Color(0xFF18385E)
private val SurfaceColor = Color(0xFF213857).copy(alpha = 0.72f)
private val AccentColor = Color(0xFFB9CCFF)
private val TextSecondary = Color(0xFFD8E4FF)
private val CardBorder = Color(0xFF2F4A6E).copy(alpha = 0.6f)

@Composable
fun PersonaMarketplaceScreen(
    onBack: () -> Unit,
    viewModel: PersonaMarketplaceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filteredPersonas = remember(state.listings, state.searchQuery, state.selectedCategory) {
        state.listings.filter { p ->
            val matchesSearch = state.searchQuery.isBlank() ||
                p.name.contains(state.searchQuery, ignoreCase = true) ||
                p.description.contains(state.searchQuery, ignoreCase = true) ||
                p.author.contains(state.searchQuery, ignoreCase = true)
            val matchesCategory = state.selectedCategory == null || p.category == state.selectedCategory
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
                    text = "Persona-Marktplatz",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Personas durchsuchen...", color = TextSecondary.copy(alpha = 0.5f)) },
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

            val categories = listOf(
                null to "Alle",
                PersonaCategory.PRODUCTIVITY to "Produktivität",
                PersonaCategory.TECHNICAL to "Technisch",
                PersonaCategory.CREATIVE to "Kreativ",
                PersonaCategory.EDUCATION to "Bildung",
                PersonaCategory.HEALTH to "Gesundheit",
                PersonaCategory.ENTERTAINMENT to "Unterhaltung"
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { (cat, label) ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { viewModel.setCategory(cat) },
                        label = {
                            Text(
                                text = label,
                                color = if (state.selectedCategory == cat) Color.White else TextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (state.selectedCategory == cat) AccentColor.copy(alpha = 0.25f)
                            else SurfaceColor,
                            selectedContainerColor = AccentColor.copy(alpha = 0.25f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = CardBorder,
                            selectedBorderColor = AccentColor.copy(alpha = 0.5f),
                            enabled = true,
                            selected = state.selectedCategory == cat
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredPersonas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keine Personas gefunden.",
                        color = TextSecondary.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPersonas, key = { it.id }) { persona ->
                        PersonaCard(
                            persona = persona,
                            onClick = { viewModel.selectPersona(persona) },
                            onInstall = { viewModel.toggleInstall(persona.id) }
                        )
                    }
                }
            }
        }
    }

    val selectedPersona = state.selectedPersona
    if (state.showDetail && selectedPersona != null) {
        PersonaDetailSheet(
            persona = selectedPersona,
            onDismiss = viewModel::dismissDetail,
            onInstall = { viewModel.toggleInstall(selectedPersona.id) }
        )
    }
}

@Composable
private fun PersonaCard(
    persona: PersonaListing,
    onClick: () -> Unit,
    onInstall: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonaAvatar(name = persona.name, category = persona.category)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = persona.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${persona.author}",
                        color = TextSecondary.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (persona.installed) Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else AccentColor.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (!persona.installed) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (persona.installed) "Installiert" else "Installieren",
                        fontSize = 11.sp,
                        color = if (persona.installed) Color(0xFF4CAF50) else AccentColor
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(100),
                    color = categoryColor(persona.category).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = persona.category.label,
                        color = categoryColor(persona.category),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i ->
                        Icon(
                            imageVector = when {
                                i < persona.rating.toInt() -> Icons.Default.Star
                                i < persona.rating.toInt() + 1 && persona.rating % 1 >= 0.5f -> Icons.AutoMirrored.Filled.StarHalf
                                else -> Icons.Default.StarOutline
                            },
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = "%.1f".format(persona.rating),
                        color = TextSecondary.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            Text(
                text = persona.description,
                color = TextSecondary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            StatBars(persona = persona)
        }
    }
}

@Composable
private fun PersonaAvatar(name: String, category: PersonaCategory) {
    val initials = name.split(" ").take(2).joinToString("") { it.first().uppercase() }
    val avatarColor = categoryColor(category)

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(avatarColor.copy(alpha = 0.6f), avatarColor.copy(alpha = 0.3f))
                )
            )
            .border(1.dp, avatarColor.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun StatBars(persona: PersonaListing) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatBar(label = "Empathie", value = persona.empathyLevel, color = Color(0xFF81C784))
        StatBar(label = "Kreativität", value = persona.creativityLevel, color = Color(0xFFFFB74D))
        StatBar(label = "Direktheit", value = persona.directnessLevel, color = Color(0xFF64B5F6))
    }
}

@Composable
private fun StatBar(label: String, value: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(64.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = value / 10f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        Text(
            text = "$value/10",
            color = TextSecondary.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaDetailSheet(
    persona: PersonaListing,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF132339),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonaAvatar(name = persona.name, category = persona.category)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = persona.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "von ${persona.author}",
                        color = TextSecondary.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = TextSecondary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(100),
                    color = categoryColor(persona.category).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = persona.category.label,
                        color = categoryColor(persona.category),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i ->
                        Icon(
                            imageVector = when {
                                i < persona.rating.toInt() -> Icons.Default.Star
                                i < persona.rating.toInt() + 1 && persona.rating % 1 >= 0.5f -> Icons.AutoMirrored.Filled.StarHalf
                                else -> Icons.Default.StarOutline
                            },
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                Text(
                    text = formatPersonaDownloads(persona.downloads),
                    color = TextSecondary.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                text = persona.description,
                color = TextSecondary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Persönlichkeits-Profil",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    StatBars(persona = persona)
                }
            }

            Button(
                onClick = onInstall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (persona.installed) Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else AccentColor.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    if (persona.installed) Icons.Default.Close else Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (persona.installed) "Deinstallieren" else "Persona installieren",
                    fontWeight = FontWeight.SemiBold,
                    color = if (persona.installed) Color(0xFF4CAF50) else AccentColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun categoryColor(category: PersonaCategory): Color = when (category) {
    PersonaCategory.PRODUCTIVITY -> Color(0xFF81C784)
    PersonaCategory.CREATIVE -> Color(0xFFFFB74D)
    PersonaCategory.TECHNICAL -> Color(0xFF64B5F6)
    PersonaCategory.EDUCATION -> Color(0xFF4DD0E1)
    PersonaCategory.HEALTH -> Color(0xFFBA68C8)
    PersonaCategory.ENTERTAINMENT -> Color(0xFFFF8A65)
    PersonaCategory.CUSTOM -> Color(0xFF90A4AE)
}

private fun formatPersonaDownloads(count: Int): String = when {
    count >= 1000 -> "%.1fk".format(count / 1000f)
    else -> count.toString()
}
