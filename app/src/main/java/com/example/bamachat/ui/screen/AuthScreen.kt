package com.example.bamachat.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.bamachat.R
import com.example.bamachat.ui.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val isAuthenticated by authViewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()

    var isLoginMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val defaultWebClientId = remember(context) {
        runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
    }
    val googleSignInClient = remember(defaultWebClientId) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .apply {
                if (defaultWebClientId.isNotBlank()) {
                    requestIdToken(defaultWebClientId)
                }
            }
            .build()
        GoogleSignIn.getClient(context, options)
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken.orEmpty()
            authViewModel.signInWithGoogleIdToken(idToken)
        } catch (e: Exception) {
            val details = e.message?.takeIf { it.isNotBlank() } ?: "Unbekannter Fehler"
            authViewModel.showError("Google-Login fehlgeschlagen: $details")
        }
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
                        authViewModel.clearError()
                        if (defaultWebClientId.isBlank()) {
                            authViewModel.showError(
                                "Google-Login ist nicht konfiguriert (default_web_client_id fehlt)."
                            )
                            return@Button
                        }
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
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
