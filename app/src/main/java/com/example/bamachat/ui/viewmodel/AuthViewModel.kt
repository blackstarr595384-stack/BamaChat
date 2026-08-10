package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.model.UserProfile
import com.example.bamachat.util.AppTelemetry
import com.example.bamachat.util.LocalDataSanitizer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL

internal data class AuthSessionResolution(
    val guestModeActive: Boolean,
    val sessionActive: Boolean,
    val accountAuthenticated: Boolean
)

internal fun resolveAuthSession(
    firebaseUserPresent: Boolean,
    storedGuestMode: Boolean
): AuthSessionResolution {
    val guestModeActive = storedGuestMode && !firebaseUserPresent
    return AuthSessionResolution(
        guestModeActive = guestModeActive,
        sessionActive = firebaseUserPresent || guestModeActive,
        accountAuthenticated = firebaseUserPresent
    )
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    companion object {
        private const val KEY_GUEST_MODE = "guest_mode_enabled"
        private const val CLEAR_CREDENTIAL_STATE_TIMEOUT_MS = 3_500L
        private const val ACCOUNT_DELETE_ENDPOINT =
            "https://europe-west1-bamachat-d07fb.cloudfunctions.net/deleteAccount"
    }

    private val prefs = application.getSharedPreferences("settings", Application.MODE_PRIVATE)
    private val dataSanitizer = LocalDataSanitizer(application.applicationContext)
    private val credentialManager: CredentialManager? = runCatching {
        CredentialManager.create(application.applicationContext)
    }.onFailure {
        AppTelemetry.logError("auth_credential_manager_init", it)
    }.getOrNull()
    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }
        .onFailure { AppTelemetry.logError("auth_init", it) }
        .getOrNull()
    private val firestore: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }
        .onFailure { AppTelemetry.logError("firestore_init", it) }
        .getOrNull()
    private val storage: FirebaseStorage? = runCatching { FirebaseStorage.getInstance() }
        .onFailure { AppTelemetry.logError("storage_init", it) }
        .getOrNull()

    private val _firebaseUser = MutableStateFlow<FirebaseUser?>(auth?.currentUser)
    val firebaseUser: StateFlow<FirebaseUser?> = _firebaseUser.asStateFlow()

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _isGuestMode = MutableStateFlow(
        resolveAuthSession(
            firebaseUserPresent = auth?.currentUser != null,
            storedGuestMode = prefs.getBoolean(KEY_GUEST_MODE, false)
        ).guestModeActive
    )
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(auth?.currentUser != null || _isGuestMode.value)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()
    private val _isEmailVerified = MutableStateFlow(false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()
    private val _connectedProviders = MutableStateFlow<List<String>>(emptyList())
    val connectedProviders: StateFlow<List<String>> = _connectedProviders.asStateFlow()


    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _firebaseUser.value = firebaseAuth.currentUser
        val hasUser = firebaseAuth.currentUser != null
        if (hasUser) {
            setGuestMode(false)
            AppTelemetry.setUserId(firebaseAuth.currentUser?.uid)
            AppTelemetry.logEvent("auth_state_signed_in")
            refreshProviderData()
            viewModelScope.launch {
                runCatching { loadProfileForCurrentUser() }
                    .onFailure {
                        _errorMessage.value = "Profil konnte nicht geladen werden."
                        _profile.value = fallbackProfileFromCurrentUser()
                    }
            }
        } else {
            _profile.value = null
            _connectedProviders.value = emptyList()
            _isEmailVerified.value = false
            AppTelemetry.setUserId(null)
            AppTelemetry.logEvent("auth_state_signed_out")
        }
        refreshAuthState()
    }

    init {
        if (auth == null) {
            AppTelemetry.logEvent("firebase_auth_unavailable")
            if (!_isGuestMode.value) {
                _statusMessage.value = "Cloud-Login ist aktuell nicht verfügbar. Gastmodus funktioniert weiter."
            }
        } else {
            auth.addAuthStateListener(authStateListener)
            if (auth.currentUser != null) {
                viewModelScope.launch {
                    runCatching { loadProfileForCurrentUser() }
                        .onFailure { _profile.value = fallbackProfileFromCurrentUser() }
                }
            }
        }
        refreshAuthState()
    }

    fun signIn(email: String, password: String) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _errorMessage.value = "Login aktuell nicht verfügbar (Firebase nicht initialisiert)."
            AppTelemetry.logEvent("login_blocked_no_firebase")
            return
        }
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _errorMessage.value = "Bitte E-Mail und Passwort eingeben."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val startMs = System.currentTimeMillis()
            try {
                firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
                setGuestMode(false)
                AppTelemetry.logEvent(
                    "login_success",
                    mapOf("duration_ms" to (System.currentTimeMillis() - startMs).toString())
                )
                loadProfileForCurrentUser()
            } catch (e: Exception) {
                AppTelemetry.logError("auth_sign_in", e)
                AppTelemetry.logEvent(
                    "login_failed",
                    mapOf("duration_ms" to (System.currentTimeMillis() - startMs).toString())
                )
                _errorMessage.value = e.message ?: "Login fehlgeschlagen."
            } finally {
                _isLoading.value = false
                refreshAuthState()
            }
        }
    }

    fun register(displayName: String, email: String, password: String) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _errorMessage.value = "Registrierung aktuell nicht verfügbar (Firebase nicht initialisiert)."
            AppTelemetry.logEvent("register_blocked_no_firebase")
            return
        }
        val cleanName = displayName.trim()
        val cleanEmail = email.trim()
        if (cleanName.isBlank() || cleanEmail.isBlank() || password.length < 6) {
            _errorMessage.value = "Name, gültige E-Mail und Passwort (mind. 6 Zeichen) erforderlich."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val startMs = System.currentTimeMillis()
            try {
                firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
                setGuestMode(false)
                firebaseAuth.currentUser?.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                )?.await()
                val uid = firebaseAuth.currentUser?.uid.orEmpty()
                if (uid.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    val profile = UserProfile(
                        uid = uid,
                        displayName = cleanName,
                        email = cleanEmail,
                        photoUrl = "",
                        createdAt = now,
                        updatedAt = now
                    )
                    saveProfile(profile)
                    _profile.value = profile
                }
                AppTelemetry.logEvent(
                    "register_success",
                    mapOf("duration_ms" to (System.currentTimeMillis() - startMs).toString())
                )
            } catch (e: Exception) {
                AppTelemetry.logError("auth_register", e)
                AppTelemetry.logEvent(
                    "register_failed",
                    mapOf("duration_ms" to (System.currentTimeMillis() - startMs).toString())
                )
                _errorMessage.value = e.message ?: "Registrierung fehlgeschlagen."
            } finally {
                _isLoading.value = false
                refreshAuthState()
            }
        }
    }

    fun continueAsGuest() {
        setGuestMode(true)
        AppTelemetry.setUserId("guest")
        AppTelemetry.logEvent("continue_as_guest")
        refreshAuthState()
    }

    fun signInWithGoogleIdToken(idToken: String) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _errorMessage.value = "Google-Login aktuell nicht verfügbar (Firebase nicht initialisiert)."
            AppTelemetry.logEvent("google_login_blocked_no_firebase")
            return
        }
        val cleanToken = idToken.trim()
        if (cleanToken.isBlank()) {
            _errorMessage.value = "Google-Login fehlgeschlagen: Ungültiges Token."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val startMs = System.currentTimeMillis()
            try {
                val credential = GoogleAuthProvider.getCredential(cleanToken, null)
                firebaseAuth.signInWithCredential(credential).await()
                setGuestMode(false)
                AppTelemetry.logEvent(
                    "login_google_success",
                    mapOf("duration_ms" to (System.currentTimeMillis() - startMs).toString())
                )
                loadProfileForCurrentUser()
            } catch (e: Exception) {
                AppTelemetry.logError("auth_google_sign_in", e)
                AppTelemetry.logEvent(
                    "login_google_failed",
                    mapOf("duration_ms" to (System.currentTimeMillis() - startMs).toString())
                )
                _errorMessage.value = e.message ?: "Google-Login fehlgeschlagen."
            } finally {
                _isLoading.value = false
                refreshAuthState()
            }
        }
    }

    fun updateDisplayName(displayName: String) {
        val user = auth?.currentUser ?: return
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) {
            _errorMessage.value = "Name darf nicht leer sein."
            return
        }
        if (cleanName == (user.displayName ?: "").trim()) {
            _statusMessage.value = "Name ist bereits aktuell."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                ).await()

                val current = _profile.value
                val now = System.currentTimeMillis()
                val updated = UserProfile(
                    uid = user.uid,
                    displayName = cleanName,
                    email = user.email.orEmpty(),
                    photoUrl = current?.photoUrl.orEmpty(),
                    createdAt = current?.createdAt ?: now,
                    updatedAt = now
                )
                saveProfile(updated)
                _profile.value = updated
                _statusMessage.value = "Name gespeichert."
            } catch (e: Exception) {
                AppTelemetry.logError("auth_update_name", e)
                _errorMessage.value = e.message ?: "Name konnte nicht gespeichert werden."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadProfileImage(imageUri: Uri) {
        val user = auth?.currentUser ?: return
        val firebaseStorage = storage
        if (firebaseStorage == null) {
            _errorMessage.value = "Profilbild-Upload aktuell nicht verfügbar."
            AppTelemetry.logEvent("profile_image_upload_blocked_no_storage")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val ref = firebaseStorage.reference.child("profile_images/${user.uid}.jpg")
                ref.putFile(imageUri).await()
                val downloadUri = ref.downloadUrl.await()

                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setPhotoUri(downloadUri)
                        .build()
                ).await()

                val current = _profile.value
                val now = System.currentTimeMillis()
                val updated = UserProfile(
                    uid = user.uid,
                    displayName = user.displayName.orEmpty(),
                    email = user.email.orEmpty(),
                    photoUrl = downloadUri.toString(),
                    createdAt = current?.createdAt ?: now,
                    updatedAt = now
                )
                saveProfile(updated)
                _profile.value = updated
                _statusMessage.value = "Profilbild gespeichert."
            } catch (e: Exception) {
                AppTelemetry.logError("auth_upload_profile_image", e)
                _errorMessage.value = e.message ?: "Profilbild-Upload fehlgeschlagen."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _errorMessage.value = "Passwort-Reset aktuell nicht verfügbar."
            return
        }
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            _errorMessage.value = "Bitte E-Mail-Adresse eingeben."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                firebaseAuth.sendPasswordResetEmail(cleanEmail).await()
                _statusMessage.value = "Passwort-Reset-E-Mail gesendet. Bitte prüfe dein Postfach."
                AppTelemetry.logEvent("password_reset_sent")
            } catch (e: Exception) {
                AppTelemetry.logError("auth_password_reset", e)
                _errorMessage.value = e.message ?: "Passwort-Reset fehlgeschlagen."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendEmailVerification() {
        val user = auth?.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                user.sendEmailVerification().await()
                _statusMessage.value = "Bestätigungs-E-Mail gesendet."
                AppTelemetry.logEvent("email_verification_sent")
            } catch (e: Exception) {
                AppTelemetry.logError("auth_email_verification", e)
                _errorMessage.value = e.message ?: "Bestätigungs-E-Mail konnte nicht gesendet werden."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshEmailVerificationStatus() {
        val user = auth?.currentUser ?: return
        viewModelScope.launch {
            try {
                user.reload().await()
                _isEmailVerified.value = user.isEmailVerified
                if (user.isEmailVerified) {
                    _statusMessage.value = "E-Mail bestätigt."
                }
            } catch (_: Exception) { }
        }
    }

    fun linkGoogleAccount(idToken: String) {
        val currentUser = auth?.currentUser ?: return
        val cleanToken = idToken.trim()
        if (cleanToken.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val credential = GoogleAuthProvider.getCredential(cleanToken, null)
                currentUser.linkWithCredential(credential).await()
                _statusMessage.value = "Google-Konto erfolgreich verbunden."
                refreshProviderData()
                AppTelemetry.logEvent("google_account_linked")
            } catch (e: Exception) {
                AppTelemetry.logError("auth_link_google", e)
                _errorMessage.value = e.message ?: "Google-Konto konnte nicht verbunden werden."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun unlinkProvider(providerId: String) {
        val currentUser = auth?.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                currentUser.unlink(providerId).await()
                _statusMessage.value = "Anmeldemethode getrennt."
                refreshProviderData()
                AppTelemetry.logEvent("provider_unlinked", mapOf("provider" to providerId))
            } catch (e: Exception) {
                AppTelemetry.logError("auth_unlink_provider", e)
                _errorMessage.value = e.message ?: "Trennen fehlgeschlagen."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshProviderData() {
        val user = auth?.currentUser
        if (user == null) {
            _connectedProviders.value = emptyList()
            _isEmailVerified.value = false
            return
        }
        _isEmailVerified.value = user.isEmailVerified
        _connectedProviders.value = user.providerData
            .mapNotNull { it.providerId }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun signOut(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null

            val wasGuest = _isGuestMode.value
            var completionInvoked = false

            try {
                auth?.signOut()

                credentialManager?.let { manager ->
                    val cleared = withTimeoutOrNull(CLEAR_CREDENTIAL_STATE_TIMEOUT_MS) {
                        runCatching {
                            manager.clearCredentialState(ClearCredentialStateRequest())
                        }.onFailure {
                            AppTelemetry.logError("auth_clear_credential_state", it)
                        }.isSuccess
                    }
                    if (cleared == null) {
                        AppTelemetry.logEvent("auth_clear_credential_state_timeout")
                    }
                }

                if (wasGuest && prefs.getBoolean("guest_auto_clear_on_signout", true)) {
                    dataSanitizer.clearGuestSessionData(clearApiKeys = false)
                }

                AppTelemetry.logEvent("logout")
            } catch (e: Exception) {
                AppTelemetry.logError("auth_sign_out", e)
                _errorMessage.value = e.message ?: "Abmeldung konnte nicht vollständig abgeschlossen werden."
            } finally {
                withContext(NonCancellable) {
                    setGuestMode(false)
                    _firebaseUser.value = auth?.currentUser
                    _profile.value = null
                    _connectedProviders.value = emptyList()
                    _isEmailVerified.value = false
                    AppTelemetry.setUserId(null)
                    refreshAuthState()
                    _isLoading.value = false
                    if (!completionInvoked) {
                        completionInvoked = true
                        onComplete()
                    }
                }
            }
        }
    }

    fun deleteAccount(onDeleted: () -> Unit = {}) {
        val firebaseAuth = auth
        val currentUser = firebaseAuth?.currentUser
        if (firebaseAuth == null || currentUser == null) {
            _errorMessage.value = "Konto-Löschung aktuell nicht verfügbar."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            val startedAt = System.currentTimeMillis()
            try {
                val idToken = currentUser.getIdToken(true).await().token?.trim().orEmpty()
                if (idToken.isBlank()) {
                    throw IllegalStateException("Aktuelle Sitzung konnte nicht bestätigt werden.")
                }

                deleteAccountRemotely(idToken)

                AppTelemetry.logEvent(
                    "account_delete_success",
                    mapOf("duration_ms" to (System.currentTimeMillis() - startedAt).toString())
                )

                signOut()
                dataSanitizer.clearAllAppData(clearApiKeys = true)
                AppTelemetry.setCollectionEnabled(false)
                _statusMessage.value = "Konto gelöscht."
                onDeleted()
            } catch (e: Exception) {
                AppTelemetry.logError("auth_delete_account", e)
                _errorMessage.value = e.message ?: "Konto konnte nicht gelöscht werden."
            } finally {
                _isLoading.value = false
                refreshAuthState()
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun showError(message: String) {
        _errorMessage.value = message
    }

    fun clearStatus() {
        _statusMessage.value = null
    }


    private suspend fun loadProfileForCurrentUser() {
        val user = auth?.currentUser ?: return
        val cloudStore = firestore ?: run {
            _profile.value = fallbackProfileFromCurrentUser()
            return
        }
        val snapshot = cloudStore.collection("users").document(user.uid).get().await()
        val profile = snapshot.toObject(UserProfile::class.java)
        if (profile != null) {
            _profile.value = profile
            return
        }

        val fallback = fallbackProfileFromCurrentUser() ?: return
        saveProfile(fallback)
        _profile.value = fallback
    }

    private suspend fun saveProfile(profile: UserProfile) {
        val cloudStore = firestore ?: return
        cloudStore.collection("users")
            .document(profile.uid)
            .set(profile)
            .await()
    }

    private fun fallbackProfileFromCurrentUser(): UserProfile? {
        val user = auth?.currentUser ?: return null
        val now = System.currentTimeMillis()
        return UserProfile(
            uid = user.uid,
            displayName = user.displayName.orEmpty(),
            email = user.email.orEmpty(),
            photoUrl = user.photoUrl?.toString().orEmpty(),
            createdAt = now,
            updatedAt = now
        )
    }

    private fun refreshAuthState() {
        val resolution = resolveAuthSession(
            firebaseUserPresent = _firebaseUser.value != null,
            storedGuestMode = _isGuestMode.value
        )
        if (_isGuestMode.value != resolution.guestModeActive) {
            setGuestMode(resolution.guestModeActive)
        }
        _isAuthenticated.value = resolution.sessionActive
    }

    private fun setGuestMode(enabled: Boolean) {
        _isGuestMode.value = enabled
        prefs.edit().putBoolean(KEY_GUEST_MODE, enabled).apply()
    }

    private suspend fun deleteAccountRemotely(idToken: String) {
        withContext(Dispatchers.IO) {
            val connection = (URL(ACCOUNT_DELETE_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            try {
                connection.outputStream.use { output ->
                    output.write("{}".toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val responseBody = runCatching {
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }.getOrDefault("")

                if (responseCode !in 200..299) {
                    val details = responseBody.takeIf { it.isNotBlank() } ?: "HTTP $responseCode"
                    throw IllegalStateException("Konto-Löschung fehlgeschlagen: $details")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    override fun onCleared() {
        auth?.removeAuthStateListener(authStateListener)
        super.onCleared()
    }
}
