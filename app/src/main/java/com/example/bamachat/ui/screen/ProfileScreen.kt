package com.example.bamachat.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bamachat.ui.theme.AppDesignSystem
import com.example.bamachat.ui.viewmodel.AuthViewModel

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    designPreset: String,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val user by authViewModel.firebaseUser.collectAsStateWithLifecycle()
    val profile by authViewModel.profile.collectAsStateWithLifecycle()
    val isGuest by authViewModel.isGuestMode.collectAsStateWithLifecycle()
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()
    val statusMessage by authViewModel.statusMessage.collectAsStateWithLifecycle()
    val palette = remember(designPreset) { AppDesignSystem.paletteForStored(designPreset) }

    var nameInput by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) authViewModel.uploadProfileImage(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(palette.screenBgTop, palette.screenBgMid, palette.screenBgBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                }
                Text(
                    text = "Profil",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.heroTitle,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.surface.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isGuest) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(80.dp)
                        )
                        Text("Gastmodus aktiv", color = palette.textPrimary)
                        Text(
                            "Für Profilbild und Cloud-Speicherung bitte mit Konto anmelden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        Button(onClick = {
                            authViewModel.signOut()
                            onRequireLogin()
                        }) {
                            Text("Zur Anmeldung")
                        }
                        return@Surface
                    }

                    val photoUrl = profile?.photoUrl.orEmpty()
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profilbild",
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(92.dp)
                        )
                    }

                    Button(
                        enabled = !isLoading,
                        onClick = { imagePicker.launch("image/*") }
                    ) {
                        Text("Profilbild wählen")
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            authViewModel.clearStatus()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Anzeigename") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.surfaceBorder,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedLabelColor = palette.textSecondary,
                            unfocusedLabelColor = palette.textSecondary,
                            cursorColor = palette.accent
                        )
                    )

                    OutlinedTextField(
                        value = user?.email.orEmpty(),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-Mail") },
                        enabled = false,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = palette.surfaceBorder,
                            disabledTextColor = palette.textSecondary,
                            disabledLabelColor = palette.textSecondary
                        )
                    )

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        onClick = {
                            authViewModel.clearError()
                            authViewModel.updateDisplayName(nameInput)
                        }
                    ) {
                        Text("Name speichern")
                    }

                    TextButton(onClick = {
                        authViewModel.signOut()
                        onRequireLogin()
                    }) {
                        Text("Abmelden")
                    }

                    if (isLoading) {
                        CircularProgressIndicator()
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!statusMessage.isNullOrBlank()) {
                        Text(
                            text = statusMessage.orEmpty(),
                            color = palette.accent,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
