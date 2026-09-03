package com.example.bamachat.ui.screen

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
}
