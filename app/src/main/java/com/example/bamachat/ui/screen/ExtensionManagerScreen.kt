package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.viewmodel.ExtensionManagerViewModel
import com.example.bamachat.ui.viewmodel.ManagedExtension
import com.example.bamachat.util.ExtensionCapability

@Composable
fun ExtensionManagerScreen(
    extensionManagerViewModel: ExtensionManagerViewModel,
    designPreset: String,
    onBack: () -> Unit
) {
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }
    val managedExtensions by extensionManagerViewModel.managedExtensions.collectAsStateWithLifecycle()
    val orphanedStates by extensionManagerViewModel.orphanedInstalledStates.collectAsStateWithLifecycle()
    val statusMessage by extensionManagerViewModel.statusMessage.collectAsStateWithLifecycle()
    val errorMessage by extensionManagerViewModel.errorMessage.collectAsStateWithLifecycle()

    val installed = remember(managedExtensions) { managedExtensions.filter { it.isInstalled } }
    val discover = remember(managedExtensions) { managedExtensions.filterNot { it.isInstalled } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.screenBgTop, palette.screenBgMid, palette.screenBgBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "AI Extensions",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Plugins installieren, Rechte vergeben, Features steuern",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                MessageBanner(
                    text = statusMessage.orEmpty(),
                    isError = false
                )
            }
            if (!errorMessage.isNullOrBlank()) {
                MessageBanner(
                    text = errorMessage.orEmpty(),
                    isError = true
                )
            }

            SectionContainer(
                title = "Installiert (${installed.size})",
                subtitle = "Aktive Erweiterungen für deinen Workspace"
            ) {
                if (installed.isEmpty()) {
                    EmptySectionHint(text = "Noch keine Extension installiert.")
                } else {
                    installed.forEach { item ->
                        ExtensionCard(
                            item = item,
                            onInstall = {},
                            onUninstall = { extensionManagerViewModel.uninstallExtension(item.manifest.id) },
                            onToggleEnabled = { enabled ->
                                extensionManagerViewModel.setExtensionEnabled(item.manifest.id, enabled)
                            },
                            onGrantAllRequired = {
                                extensionManagerViewModel.grantAllRequiredCapabilities(item.manifest.id)
                            },
                            onToggleCapability = { capability, grant ->
                                if (grant) {
                                    extensionManagerViewModel.grantCapability(item.manifest.id, capability)
                                } else {
                                    extensionManagerViewModel.revokeCapability(item.manifest.id, capability)
                                }
                            },
                            paletteSurface = palette.surface.copy(alpha = 0.94f),
                            paletteBorder = palette.surfaceBorder
                        )
                    }
                }
            }

            SectionContainer(
                title = "Entdecken (${discover.size})",
                subtitle = "Kuratiertes Extension-Katalogpaket"
            ) {
                if (discover.isEmpty()) {
                    EmptySectionHint(text = "Alle verfügbaren Extensions sind bereits installiert.")
                } else {
                    discover.forEach { item ->
                        ExtensionCard(
                            item = item,
                            onInstall = { extensionManagerViewModel.installExtension(item.manifest.id) },
                            onUninstall = {},
                            onToggleEnabled = {},
                            onGrantAllRequired = {},
                            onToggleCapability = { _, _ -> },
                            paletteSurface = palette.surface.copy(alpha = 0.9f),
                            paletteBorder = palette.surfaceBorder
                        )
                    }
                }
            }

            if (orphanedStates.isNotEmpty()) {
                SectionContainer(
                    title = "Legacy-Extensions (${orphanedStates.size})",
                    subtitle = "Installiert, aber nicht mehr im aktuellen Katalog"
                ) {
                    orphanedStates.forEach { state ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF2D2134).copy(alpha = 0.93f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = state.extensionId,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Bitte prüfen und ggf. entfernen.",
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { extensionManagerViewModel.uninstallExtension(state.extensionId) }
                                ) {
                                    Text("Entfernen")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MessageBanner(text: String, isError: Boolean) {
    val start = if (isError) Color(0xFF9C2F3A) else Color(0xFF1F6D55)
    val end = if (isError) Color(0xFF4E1F2D) else Color(0xFF1D3644)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(start, end)))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.WarningAmber else Icons.Default.Extension,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SectionContainer(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF132339).copy(alpha = 0.84f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
            content()
        }
    }
}

@Composable
private fun EmptySectionHint(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ExtensionCard(
    item: ManagedExtension,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onGrantAllRequired: () -> Unit,
    onToggleCapability: (ExtensionCapability, Boolean) -> Unit,
    paletteSurface: Color,
    paletteBorder: Color
) {
    val manifest = item.manifest
    val capabilities = remember(manifest) {
        manifest.allCapabilities.sortedWith(
            compareByDescending<ExtensionCapability> { it in manifest.requiredCapabilities }
                .thenBy { it.label }
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = paletteSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, paletteBorder.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = Color(0xFFB8CCFF),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manifest.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${manifest.category} • v${manifest.version}",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (item.isInstalled) {
                    Switch(
                        checked = item.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                }
            }

            Text(
                text = manifest.description,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(
                    text = if (item.isInstalled) "Installiert" else "Verfügbar",
                    container = Color.White.copy(alpha = 0.14f)
                )
                if (manifest.experimental) {
                    StatusBadge(
                        text = "Experimentell",
                        container = Color(0xFF7B4F9B).copy(alpha = 0.38f)
                    )
                }
            }

            if (item.isInstalled && item.missingRequiredCapabilities.isNotEmpty()) {
                Text(
                    text = "Pflichtrechte fehlen: ${
                        item.missingRequiredCapabilities.joinToString { it.label }
                    }",
                    color = Color(0xFFFFC8C8),
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onGrantAllRequired) {
                    Text("Pflichtrechte freigeben")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Berechtigungen",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                capabilities.forEach { capability ->
                    val isRequired = capability in manifest.requiredCapabilities
                    val isGranted = item.grantedCapabilities.contains(capability)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = capability.label +
                                        if (isRequired) " (Pflicht)" else " (Optional)",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${capability.description} • Risiko: ${capability.risk.label}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (item.isInstalled) {
                                OutlinedButton(
                                    onClick = { onToggleCapability(capability, !isGranted) }
                                ) {
                                    Text(if (isGranted) "Entziehen" else "Freigeben")
                                }
                            }
                        }
                    }
                }
            }

            if (item.isInstalled) {
                OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) {
                    Text("Deinstallieren")
                }
            } else {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Text("Installieren")
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
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
