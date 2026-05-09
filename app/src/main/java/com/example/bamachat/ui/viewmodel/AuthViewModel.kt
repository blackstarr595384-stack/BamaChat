package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.net.Uri
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val KEY_GUEST_MODE = "guest_mode_enabled"
    }

    private val prefs = application.getSharedPreferences("settings", Application.MODE_PRIVATE)
    private val dataSanitizer = LocalDataSanitizer(application.applicationContext)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _firebaseUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val firebaseUser: StateFlow<FirebaseUser?> = _firebaseUser.asStateFlow()

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _isGuestMode = MutableStateFlow(prefs.getBoolean(KEY_GUEST_MODE, false))
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(auth.currentUser != null || _isGuestMode.value)
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
        auth.addAuthStateListener(authStateListener)
        if (auth.currentUser != null) {
            viewModelScope.launch {
                runCatching { loadProfileForCurrentUser() }
                    .onFailure { _profile.value = fallbackProfileFromCurrentUser() }
            }
        }
        refreshAuthState()
    }

    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _errorMessage.value = "Bitte E-Mail und Passwort eingeben."
            return
        }
        val wasGuestBeforeSignIn = _isGuestMode.value
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                auth.signInWithEmailAndPassword(cleanEmail, password).await()
                if (wasGuestBeforeSignIn && prefs.getBoolean("guest_auto_clear_on_account_signin", true)) {
                    dataSanitizer.clearGuestSessionData(clearApiKeys = false)
                }
                AppTelemetry.logEvent("login_success")
                loadProfileForCurrentUser()
            } catch (e: Exception) {
                AppTelemetry.logError("auth_sign_in", e)
                _errorMessage.value = e.message ?: "Login fehlgeschlagen."
            } finally {
                _isLoading.value = false
                refreshAuthState()
            }
        }
    }

    fun register(displayName: String, email: String, password: String) {
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
            try {
                auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                if (wasGuestBeforeRegister && prefs.getBoolean("guest_auto_clear_on_account_signin", true)) {
                    dataSanitizer.clearGuestSessionData(clearApiKeys = false)
                }
                auth.currentUser?.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                )?.await()
                val uid = auth.currentUser?.uid.orEmpty()
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
                AppTelemetry.logEvent("register_success")
            } catch (e: Exception) {
                AppTelemetry.logError("auth_register", e)
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
        val cleanToken = idToken.trim()
        if (cleanToken.isBlank()) {
            _errorMessage.value = "Google-Login fehlgeschlagen: Ungültiges Token."
            return
        }

        val wasGuestBeforeSignIn = _isGuestMode.value
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val credential = GoogleAuthProvider.getCredential(cleanToken, null)
                auth.signInWithCredential(credential).await()
                if (wasGuestBeforeSignIn && prefs.getBoolean("guest_auto_clear_on_account_signin", true)) {
                    dataSanitizer.clearGuestSessionData(clearApiKeys = false)
                }
                AppTelemetry.logEvent("login_google_success")
                loadProfileForCurrentUser()
            } catch (e: Exception) {
                AppTelemetry.logError("auth_google_sign_in", e)
                _errorMessage.value = e.message ?: "Google-Login fehlgeschlagen."
            } finally {
                _isLoading.value = false
                refreshAuthState()
            }
        }
    }

    fun updateDisplayName(displayName: String) {
        val user = auth.currentUser ?: return
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
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val ref = storage.reference.child("profile_images/${user.uid}.jpg")
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
        auth.signOut()
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
        val user = auth.currentUser ?: return
        val snapshot = firestore.collection("users").document(user.uid).get().await()
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
        firestore.collection("users")
            .document(profile.uid)
            .set(profile)
            .await()
    }

    private fun fallbackProfileFromCurrentUser(): UserProfile? {
        val user = auth.currentUser ?: return null
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

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }
}
