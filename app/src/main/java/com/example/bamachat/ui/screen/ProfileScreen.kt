package com.example.bamachat.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bamachat.ui.theme.NeonPurple
import com.example.bamachat.ui.theme.NeonCyan
import com.example.bamachat.ui.theme.NeonPink
import com.example.bamachat.ui.theme.SurfaceDarkCard
import com.example.bamachat.ui.theme.SurfaceDarkElevated
import com.example.bamachat.ui.theme.TextSecondary
import com.example.bamachat.ui.theme.NeonGreen
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

    var nameInput by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) authViewModel.uploadProfileImage(uri)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "profileGlow")
    val avatarGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0D0D1A),
                        Color(0xFF14142A),
                        Color(0xFF1A1A2E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "👤 Profil",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isGuest) {
                // Guest mode card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                        .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = NeonPurple.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceDarkElevated
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        NeonPurple.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                ),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .shadow(12.dp, CircleShape, spotColor = NeonPurple.copy(alpha = 0.3f))
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(NeonPurple.copy(alpha = 0.3f), NeonPurple.copy(alpha = 0.1f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Gast-Modus",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Melde dich an, um dein Profil zu personalisieren",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onRequireLogin,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Text("Anmelden", color = Color.White)
                        }
                    }
                }
            } else {
                // Profile card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = NeonPurple.copy(alpha = 0.2f)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceDarkElevated
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        NeonPurple.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                ),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .shadow(
                                    elevation = (12.dp * (1f + avatarGlow * 0.5f)),
                                    shape = CircleShape,
                                    spotColor = NeonPurple.copy(alpha = avatarGlow * 0.3f)
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(NeonPurple.copy(alpha = 0.3f), NeonPurple.copy(alpha = 0.1f))
                                    )
                                )
                                .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profile?.photoUrl != null) {
                                AsyncImage(
                                    model = profile?.photoUrl,
                                    contentDescription = "Profilbild",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (user?.photoUrl != null) {
                                AsyncImage(
                                    model = user?.photoUrl,
                                    contentDescription = "Profilbild",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = NeonPurple,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                            // Camera overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .shadow(4.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(NeonPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Foto ändern",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Display name
                        Text(
                            text = profile?.displayName?.takeIf { it.isNotBlank() } ?: user?.displayName ?: "Nutzer",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = user?.email.orEmpty(),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Edit profile section
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = NeonCyan.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceDarkCard.copy(alpha = 0.6f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Profil bearbeiten",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Anzeigename") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceDarkElevated,
                                unfocusedContainerColor = SurfaceDarkElevated,
                                focusedBorderColor = NeonPurple.copy(alpha = 0.4f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                cursorColor = NeonPurple,
                                focusedLabelColor = NeonPurple,
                                unfocusedLabelColor = TextSecondary
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )

                        OutlinedTextField(
                            value = user?.email.orEmpty(),
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("E-Mail") },
                            enabled = false,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledContainerColor = SurfaceDarkElevated,
                                disabledBorderColor = Color.White.copy(alpha = 0.08f),
                                disabledTextColor = TextSecondary,
                                disabledLabelColor = TextSecondary
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )

                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !isLoading && nameInput.isNotBlank(),
                            onClick = {
                                authViewModel.clearError()
                                authViewModel.updateDisplayName(nameInput)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple,
                                contentColor = Color.White,
                                disabledContainerColor = SurfaceDarkElevated,
                                disabledContentColor = TextSecondary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Speichern",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // Sign out
                        TextButton(
                            onClick = {
                                authViewModel.signOut()
                                onRequireLogin()
                            },
                            enabled = !isLoading,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                "Abmelden",
                                color = NeonPink.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Status messages
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp),
                                color = NeonPurple
                            )
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF93000A).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    modifier = Modifier.padding(12.dp),
                                    color = Color(0xFFFFB4AB),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (!statusMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NeonGreen.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = statusMessage.orEmpty(),
                                    modifier = Modifier.padding(12.dp),
                                    color = NeonGreen,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
