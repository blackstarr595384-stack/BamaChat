package com.example.bamachat.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.bamachat.ui.viewmodel.AuthViewModel
import com.example.bamachat.util.AppTelemetry
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onAuthenticated: () -> Unit
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

    var isLoginMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
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
        if (
            credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Unerwarteter Credential-Typ.")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

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
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF1F2A44))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isLoginMode) "Anmelden" else "Registrieren",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mit Konto kannst du Profil und Daten geräteübergreifend nutzen.",
                    style = MaterialTheme.typography.bodySmall
                )

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
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                )

                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Passwort verbergen" else "Passwort anzeigen")
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    onClick = {
                        AppTelemetry.logEvent(if (isLoginMode) "login_submit_clicked" else "register_submit_clicked")
                        authViewModel.clearError()
                        if (isLoginMode) {
                            authViewModel.signIn(email = email, password = password)
                        } else {
                            authViewModel.register(displayName = name, email = email, password = password)
                        }
                    }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                    } else {
                        Text(if (isLoginMode) "Jetzt anmelden" else "Konto erstellen")
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
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
                                    // Button-Flow ist robuster auf Geräten mit mehreren Google-Konten.
                                    requestGoogleIdTokenWithButtonFlow()
                                } catch (e: GetCredentialException) {
                                    val isCanceled = e.javaClass.simpleName.contains("Cancellation")
                                    if (isCanceled) throw e
                                    // Fallback auf Bottom-Sheet-Flow mit autorisierten/allen Konten.
                                    try {
                                        requestGoogleIdTokenWithGoogleIdOption(filterAuthorizedAccounts = true)
                                    } catch (_: NoCredentialException) {
                                        requestGoogleIdTokenWithGoogleIdOption(filterAuthorizedAccounts = false)
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
                                    authViewModel.showError("Google-Login fehlgeschlagen: $details")
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
                    }
                ) {
                    Text("Mit Google anmelden")
                }

                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        authViewModel.clearError()
                        isLoginMode = !isLoginMode
                    }
                ) {
                    Text(if (isLoginMode) "Noch kein Konto? Registrieren" else "Bereits ein Konto? Anmelden")
                }

                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { authViewModel.continueAsGuest() }
                ) {
                    Text("Als Gast fortfahren")
                }

                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
