package com.example.bamachat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestAppIdentityTest {
    @Test
    fun mainActivityResolvesForCurrentApplicationId() {
        val packageManager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        val resolved = packageManager.resolveActivity(TestAppIdentity.mainActivityIntent(), 0)

        assertNotNull("BamaChat MainActivity konnte nicht aufgelöst werden", resolved)
        assertEquals(TestAppIdentity.APPLICATION_ID, resolved?.activityInfo?.packageName)
        assertEquals(TestAppIdentity.MAIN_ACTIVITY, resolved?.activityInfo?.name)
    }
}
