package com.example.bamachat.data.auth

import com.example.bamachat.data.local.AccountAuthProvider
import com.example.bamachat.data.local.AccountAuthTransitionRunner
import com.example.bamachat.data.local.AccountTransitionResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface AccountAuthenticationGateway {
    suspend fun signInWithGoogle(idToken: String): String
    suspend fun signInWithEmail(email: String, password: String): String
    suspend fun registerWithEmail(email: String, password: String): String
}

@Singleton
class FirebaseAccountAuthenticationGateway @Inject constructor() : AccountAuthenticationGateway {
    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    override suspend fun signInWithGoogle(idToken: String): String {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        return auth.currentUser?.uid ?: error("Google-Anmeldung wurde nicht bestätigt.")
    }

    override suspend fun signInWithEmail(email: String, password: String): String {
        auth.signInWithEmailAndPassword(email, password).await()
        return auth.currentUser?.uid ?: error("Anmeldung wurde nicht bestätigt.")
    }

    override suspend fun registerWithEmail(email: String, password: String): String {
        auth.createUserWithEmailAndPassword(email, password).await()
        return auth.currentUser?.uid ?: error("Registrierung wurde nicht bestätigt.")
    }
}

@Singleton
class AccountAuthenticationCoordinator @Inject constructor(
    private val gateway: AccountAuthenticationGateway,
    private val transitionRunner: AccountAuthTransitionRunner
) {
    suspend fun signInWithGoogle(idToken: String): AccountTransitionResult =
        transitionRunner.authenticate(AccountAuthProvider.GOOGLE) {
            gateway.signInWithGoogle(idToken)
        }

    suspend fun signInWithEmail(email: String, password: String): AccountTransitionResult =
        transitionRunner.authenticate(AccountAuthProvider.EMAIL) {
            gateway.signInWithEmail(email, password)
        }

    suspend fun registerWithEmail(email: String, password: String): AccountTransitionResult =
        transitionRunner.authenticate(AccountAuthProvider.REGISTRATION) {
            gateway.registerWithEmail(email, password)
        }
}
