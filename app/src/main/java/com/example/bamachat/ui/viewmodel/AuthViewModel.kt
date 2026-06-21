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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    companion object {
        private const val KEY_GUEST_MODE = "guest_mode_enabled"
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

    private val _isGuestMode = MutableStateFlow(prefs.getBoolean(KEY_GUEST_MODE, false))
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(auth?.currentUser != null || _isGuestMode.value)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _firebaseUser.value = firebaseAuth.currentUser
        val hasUser = firebaseAuth.currentUser != null
        if (hasUser) {
            _isGuestMode.value = false
            prefs.edit().putBoolean(KEY_GUEST_MODE, false).apply()
            AppTelemetry.setUserId(firebaseAuth.currentUser?.uid)
            AppTelemetry.logEvent("auth_state_signed_in")
            viewModelScope.launch {
                runCatching { loadProfileForCurrentUser() }
                    .onFailure {
                        _errorMessage.value = "Profil konnte nicht geladen werden."
                        _profile.value = fallbackProfileFromCurrentUser()
                    }
            }
        } else {
            _profile.value = null
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
        val wasGuestBeforeSignIn = _isGuestMode.value
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val startMs = System.currentTimeMillis()
            try {
                firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
                if (wasGuestBeforeSignIn && prefs.getBoolean("guest_auto_clear_on_account_signin", true)) {
                    dataSanitizer.clearGuestSessionData(clearApiKeys = false)
                }
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
        val wasGuestBeforeRegister = _isGuestMode.value
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val startMs = System.currentTimeMillis()
            try {
                firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
                if (wasGuestBeforeRegister && prefs.getBoolean("guest_auto_clear_on_account_signin", true)) {
                    dataSanitizer.clearGuestSessionData(clearApiKeys = false)
                }
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
        _isGuestMode.value = true
        prefs.edit().putBoolean(KEY_GUEST_MODE, true).apply()
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

        val wasGuestBeforeSignIn = _isGuestMode.value
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val startMs = System.currentTimeMillis()
            try {
                val credential = GoogleAuthProvider.getCredential(cleanToken, null)
                firebaseAuth.signInWithCredential(credential).await()
                if (wasGuestBeforeSignIn && prefs.getBoolean("guest_auto_clear_on_account_signin", true)) {
                    dataSanitizer.clearGuestSessionData(clearApiKeys = false)
                }
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

    fun signOut() {
        val wasGuest = _isGuestMode.value
        auth?.signOut()
        credentialManager?.let { manager ->
            viewModelScope.launch {
                runCatching {
                    manager.clearCredentialState(ClearCredentialStateRequest())
                }.onFailure {
                    AppTelemetry.logError("auth_clear_credential_state", it)
                }
            }
        }
        if (wasGuest && prefs.getBoolean("guest_auto_clear_on_signout", true)) {
            viewModelScope.launch {
                dataSanitizer.clearGuestSessionData(clearApiKeys = false)
            }
        }
        _isGuestMode.value = false
        prefs.edit().putBoolean(KEY_GUEST_MODE, false).apply()
        _profile.value = null
        AppTelemetry.logEvent("logout")
        AppTelemetry.setUserId(null)
        refreshAuthState()
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
        _isAuthenticated.value = _firebaseUser.value != null || _isGuestMode.value
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
