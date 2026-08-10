package com.example.bamachat.ui.screen

import com.example.bamachat.ui.viewmodel.resolveAuthSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BamaChatAppVoiceSessionPolicyTest {
    @Test
    fun logoutAndAccountSwitchEndLiveVoiceSession() {
        assertTrue(shouldEndLiveVoiceSession("uid-one", null))
        assertTrue(shouldEndLiveVoiceSession("uid-one", "uid-two"))
    }

    @Test
    fun initialAuthResolutionAndStableAccountDoNotEndSession() {
        assertFalse(shouldEndLiveVoiceSession(null, null))
        assertFalse(shouldEndLiveVoiceSession(null, "uid-one"))
        assertFalse(shouldEndLiveVoiceSession("uid-one", "uid-one"))
    }

    @Test
    fun guestCanOpenAccountConnectionWithoutCompletingAuthentication() {
        val state = resolveAuthSession(firebaseUserPresent = false, storedGuestMode = true)

        assertTrue(state.guestModeActive)
        assertTrue(state.sessionActive)
        assertFalse(state.accountAuthenticated)
    }

    @Test
    fun googleSuccessAuthenticatesAndClearsGuestState() {
        val state = resolveAuthSession(firebaseUserPresent = true, storedGuestMode = true)

        assertFalse(state.guestModeActive)
        assertTrue(state.accountAuthenticated)
    }

    @Test
    fun emailSuccessAuthenticatesAndClearsGuestState() {
        val state = resolveAuthSession(firebaseUserPresent = true, storedGuestMode = true)

        assertFalse(state.guestModeActive)
        assertTrue(state.accountAuthenticated)
    }

    @Test
    fun canceledAuthenticationKeepsGuestSessionUsable() {
        val state = resolveAuthSession(firebaseUserPresent = false, storedGuestMode = true)

        assertTrue(state.guestModeActive)
        assertFalse(state.accountAuthenticated)
    }

    @Test
    fun failedAuthenticationDoesNotCompleteAccountConnection() {
        val state = resolveAuthSession(firebaseUserPresent = false, storedGuestMode = true)

        assertTrue(state.sessionActive)
        assertFalse(state.accountAuthenticated)
    }

    @Test
    fun signOutGuestAndSuccessfulReauthenticationResolveToAccount() {
        val signedOut = resolveAuthSession(firebaseUserPresent = false, storedGuestMode = false)
        val guest = resolveAuthSession(firebaseUserPresent = false, storedGuestMode = true)
        val signedIn = resolveAuthSession(firebaseUserPresent = true, storedGuestMode = true)

        assertFalse(signedOut.sessionActive)
        assertTrue(guest.guestModeActive)
        assertTrue(signedIn.accountAuthenticated)
        assertFalse(signedIn.guestModeActive)
    }

    @Test
    fun staleGuestValueCannotOverrideFirebaseUser() {
        val state = resolveAuthSession(firebaseUserPresent = true, storedGuestMode = true)

        assertTrue(state.sessionActive)
        assertTrue(state.accountAuthenticated)
        assertFalse(state.guestModeActive)
    }
}
