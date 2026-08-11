package com.example.bamachat.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionResolutionTest {
    @Test
    fun guestCanOpenAccountConnectionWithoutCompletingAuthentication() {
        val state = resolveAuthSession(firebaseUserPresent = false, storedGuestMode = true)
        assertTrue(state.guestModeActive)
        assertTrue(state.sessionActive)
        assertFalse(state.accountAuthenticated)
    }

    @Test
    fun successfulAccountAuthenticationOverridesStaleGuestState() {
        val state = resolveAuthSession(firebaseUserPresent = true, storedGuestMode = true)
        assertFalse(state.guestModeActive)
        assertTrue(state.sessionActive)
        assertTrue(state.accountAuthenticated)
    }

    @Test
    fun canceledOrFailedAuthenticationKeepsGuestSessionUsable() {
        val state = resolveAuthSession(firebaseUserPresent = false, storedGuestMode = true)
        assertTrue(state.guestModeActive)
        assertFalse(state.accountAuthenticated)
    }

    @Test
    fun signedOutStateHasNoActiveSession() {
        val state = resolveAuthSession(firebaseUserPresent = false, storedGuestMode = false)
        assertFalse(state.sessionActive)
        assertFalse(state.guestModeActive)
    }
}
