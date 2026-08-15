package com.example.bamachat.data.cloud

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

fun interface AuthenticatedUidProvider {
    fun currentUid(): String?
}

@Singleton
class FirebaseAuthenticatedUidProvider @Inject constructor() : AuthenticatedUidProvider {
    override fun currentUid(): String? =
        runCatching { FirebaseAuth.getInstance().currentUser?.uid?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
}
