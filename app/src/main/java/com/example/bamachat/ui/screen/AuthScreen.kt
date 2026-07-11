package com.example.bamachat.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.viewmodel.AuthViewModel
import com.example.bamachat.util.AppTelemetry
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

@SuppressLint("CredentialManagerSignInWithGoogle")
@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onBack: () -> Unit = {},
    onOpenHelp: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }
            .onFailure { AppTelemetry.logError("auth_credential_manager_ui_init", it) }
            .getOrNull()
    }
    val isAuthenticated by authViewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()
    val statusMessage by authViewModel.statusMessage.collectAsStateWithLifecycle()

    var isLoginMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var emailExpanded by remember { mutableStateOf(false) }

    @SuppressLint("DiscouragedApi")
    val defaultWebClientId = remember(context) {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId != 0) context.getString(resId) else ""
    }

    fun extractGoogleIdToken(credential: androidx.credentials.Credential): String {
        if (credential !is CustomCredential) {
            throw IllegalStateException("Unerwarteter Credential-Typ.")
        }
        return when (credential.type) {
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL ->
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            else -> throw IllegalStateException("Unerwarteter Credential-Typ.")
        }
    }

    @SuppressLint("CredentialManagerSignInWithGoogle")
    suspend fun requestGoogleIdTokenWithGoogleIdOption(filterAuthorizedAccounts: Boolean): String {
        val manager = credentialManager
            ?: throw IllegalStateException("Credential Manager ist auf diesem Gerät nicht verfügbar.")
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterAuthorizedAccounts)
            .setServerClientId(defaultWebClientId)
            .setAutoSelectEnabled(filterAuthorizedAccounts)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = manager.getCredential(context = context, request = request)
        return extractGoogleIdToken(response.credential)
    }

    @SuppressLint("CredentialManagerSignInWithGoogle")
    suspend fun requestGoogleIdTokenWithButtonFlow(): String {
        val manager = credentialManager
            ?: throw IllegalStateException("Credential Manager ist auf diesem Gerät nicht verfügbar.")
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(defaultWebClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()
        val response = manager.getCredential(context = context, request = request)
        return extractGoogleIdToken(response.credential)
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) onAuthenticated()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF09111E), Color(0xFF12253F), Color(0xFF18304C))
                )
            )
    ) {
        AuthBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("Zurück", color = Color.White.copy(alpha = 0.92f))
                }
                TextButton(onClick = onOpenHelp) {
                    Text("Hilfe", color = Color(0xFFD7E4FF))
                }
            }

            Text(
                text = "Konto verbinden",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    statusMessage?.takeIf { it.isNotBlank() }?.let {
                        AuthMessageCard(
                            text = it,
                            container = Color(0xFFE7F0FF),
                            content = Color(0xFF163A66)
                        )
                    }

                    errorMessage?.takeIf { it.isNotBlank() }?.let {
                        AuthMessageCard(
                            text = it,
                            container = Color(0xFFFFE2E0),
                            content = Color(0xFF7A1F1A)
                        )
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = !isLoading,
                        onClick = {
                            coroutineScope.launch {
                                authViewModel.clearError()
                                if (defaultWebClientId.isBlank()) {
                                    AppTelemetry.logEvent("google_login_missing_client_id")
                                    authViewModel.showError(
                                        "Google-Login ist nicht konfiguriert (default_web_client_id fehlt)."
                                    )
                                    return@launch
                                }

                                AppTelemetry.logEvent("google_login_start")
                                try {
                                    val idToken = try {
                                        requestGoogleIdTokenWithButtonFlow()
                                    } catch (e: GetCredentialException) {
                                        val isCanceled = e.javaClass.simpleName.contains("Cancellation")
                                        if (isCanceled) throw e
                                        try {
                                            requestGoogleIdTokenWithGoogleIdOption(
                                                filterAuthorizedAccounts = true
                                            )
                                        } catch (_: NoCredentialException) {
                                            requestGoogleIdTokenWithGoogleIdOption(
                                                filterAuthorizedAccounts = false
                                            )
                                        }
                                    }
                                    authViewModel.signInWithGoogleIdToken(idToken)
                                } catch (e: NoCredentialException) {
                                    AppTelemetry.logEvent("google_login_no_credentials")
                                    authViewModel.showError(
                                        "Kein passendes Google-Konto auf dem Gerät gefunden."
                                    )
                                } catch (e: GoogleIdTokenParsingException) {
                                    AppTelemetry.logError("google_login_token_parsing", e)
                                    authViewModel.showError(
                                        "Google-Login fehlgeschlagen: Token konnte nicht verarbeitet werden."
                                    )
                                } catch (e: GetCredentialException) {
                                    AppTelemetry.logError("google_login_get_credential", e)
                                    val isCanceled = e.javaClass.simpleName.contains("Cancellation")
                                    if (isCanceled) {
                                        AppTelemetry.logEvent("google_login_canceled")
                                    } else {
                                        val details = e.message?.takeIf { it.isNotBlank() }
                                            ?: "Unbekannter Fehler"
                                        authViewModel.showError(
                                            "Google-Login fehlgeschlagen: $details"
                                        )
                                    }
                                } catch (e: IllegalStateException) {
                                    AppTelemetry.logError("google_login_unavailable", e)
                                    authViewModel.showError(
                                        "Google-Login ist auf diesem Gerät aktuell nicht verfügbar."
                                    )
                                } catch (e: Exception) {
                                    AppTelemetry.logError("google_login_unknown", e)
                                    val details = e.message?.takeIf { it.isNotBlank() }
                                        ?: "Unbekannter Fehler"
                                    authViewModel.showError("Google-Login fehlgeschlagen: $details")
                                }
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF183A68),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Mit Google fortfahren")
                    }

                    AuthDivider("oder")

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { emailExpanded = !emailExpanded },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.06f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Mit E-Mail anmelden",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                if (emailExpanded) "▾" else "▸",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = emailExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AuthModeButton(
                                    label = "Anmelden",
                                    active = isLoginMode,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        authViewModel.clearError()
                                        isLoginMode = true
                                    }
                                )
                                AuthModeButton(
                                    label = "Registrieren",
                                    active = !isLoginMode,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        authViewModel.clearError()
                                        isLoginMode = false
                                    }
                                )
                            }

                            if (!isLoginMode) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Name") }
                                )
                            }

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("E-Mail") }
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Passwort") },
                                visualTransformation = if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showPassword = !showPassword }) {
                                    Text(if (showPassword) "Passwort verbergen" else "Passwort anzeigen")
                                }
                                if (!isLoginMode) {
                                    Text(
                                        text = "Mind. 6 Zeichen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                                    )
                                }
                            }

                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                enabled = !isLoading,
                                onClick = {
                                    AppTelemetry.logEvent(
                                        if (isLoginMode) "login_submit_clicked" else "register_submit_clicked"
                                    )
                                    authViewModel.clearError()
                                    if (isLoginMode) {
                                        authViewModel.signIn(email = email, password = password)
                                    } else {
                                        authViewModel.register(
                                            displayName = name,
                                            email = email,
                                            password = password
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF183A68),
                                    contentColor = Color.White
                                )
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Text(if (isLoginMode) "Anmelden" else "Registrieren")
                                }
                            }

                            TextButton(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                onClick = {
                                    authViewModel.clearError()
                                    isLoginMode = !isLoginMode
                                }
                            ) {
                                Text(
                                    if (isLoginMode) {
                                        "Noch kein Konto? Registrieren"
                                    } else {
                                        "Bereits ein Konto? Anmelden"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sofort loslegen",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Gastmodus ist lokal. Du kannst später ein Konto verbinden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD7E4FF)
                    )
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        onClick = {
                            authViewModel.clearError()
                            authViewModel.continueAsGuest()
                        },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Text("Als Gast fortfahren", color = Color.White.copy(alpha = 0.92f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthBackdrop() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-32).dp)
                .size(220.dp)
                .graphicsLayer(alpha = 0.92f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF8FB2FF).copy(alpha = 0.24f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-90).dp, y = 140.dp)
                .size(260.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF4A7FCC).copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun AuthModeButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = if (active) Color(0xFF173863) else Color(0xFFF1F5FB)
    val content = if (active) Color.White else Color(0xFF294565)
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, if (active) Color.Transparent else Color(0xFFD9E2F2))
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, color = content, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AuthMessageCard(text: String, container: Color, content: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = content,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AuthDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFFD7DFEE))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFFD7DFEE))
        )
    }
}
