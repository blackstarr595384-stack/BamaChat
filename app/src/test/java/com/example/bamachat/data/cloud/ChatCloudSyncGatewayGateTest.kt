package com.example.bamachat.data.cloud

import android.content.Context
import com.example.bamachat.data.local.ChatOwnerScope
import com.example.bamachat.data.local.ChatSessionScopeStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ChatCloudSyncGatewayGateTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var scopeStore: ChatSessionScopeStore
    private lateinit var uidProvider: MutableUidProvider
    private lateinit var gateway: ChatCloudSyncGateway

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        scopeStore = ChatSessionScopeStore(prefs)
        uidProvider = MutableUidProvider()
        gateway = ChatCloudSyncGateway(scopeStore, uidProvider, firestore = null)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun directGatewayRejectsGuestLegacyInactiveAndForeignAccount() {
        uidProvider.uid = "uid-a"
        scopeStore.startNewGuestSession()
        assertFalse(gateway.isDirectUploadAllowed("uid-a", scopeStore.currentScope()))
        assertFalse(gateway.isDirectUploadAllowed("uid-a", ChatOwnerScope.LEGACY_UNCLASSIFIED))
        assertFalse(gateway.isDirectUploadAllowed("uid-a", ChatOwnerScope.NO_ACTIVE_SESSION))

        scopeStore.deactivateSession()
        scopeStore.activateAccount("uid-a")
        assertFalse(gateway.isDirectUploadAllowed("uid-a", ChatOwnerScope.account("uid-b")))
        assertFalse(gateway.isDirectUploadAllowed("uid-b", ChatOwnerScope.account("uid-a")))
    }

    @Test
    fun directGatewayClosesDuringPendingAndAllowsOnlyMatchingAccountAfterCompletion() {
        uidProvider.uid = "uid-a"
        scopeStore.activateAccount("uid-a")
        assertTrue(gateway.isDirectUploadAllowed("uid-a", ChatOwnerScope.account("uid-a")))

        scopeStore.prepareAccountTransition()
        assertFalse(gateway.isDirectUploadAllowed("uid-a", ChatOwnerScope.account("uid-a")))
    }

    private class MutableUidProvider : AuthenticatedUidProvider {
        var uid: String? = null
        override fun currentUid(): String? = uid
    }

    private companion object {
        const val PREFS_NAME = "chat_cloud_sync_gateway_gate_test"
    }
}
