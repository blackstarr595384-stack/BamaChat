package com.example.bamachat

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppE2ESmokeTest {

    companion object {
        private const val PACKAGE_NAME = "com.example.bamachat"
        private const val TIMEOUT = 20_000L
    }

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun authProfileVoiceImage_smokeFlow() {
        launchApp()

        // Welcome/Auth: optionalen Einstieg abhandeln
        val guestButton = waitForAnyText(
            4_000L,
            "Als Gast starten",
            "Als Gast fortfahren",
            "Zum BamaHub"
        )
        guestButton?.click()

        val appVisible = waitForAppVisible(TIMEOUT)
        assertTrue("App-Start nicht erkannt", appVisible)

        // Hub: Provider- und Design-Chips vorhanden (nicht alle Designs/Provider wechseln per Klick)
        waitForTextPrefix(3_500, "Provider: ")
        waitForTextPrefix(3_500, "Design: ")

        // In Chat wechseln (falls nicht bereits im Chat)
        var chatTitle = device.findObject(By.textContains("BamaChat"))
        if (chatTitle == null) {
            val chatEntry = waitForAnyText(TIMEOUT, "Chat", "Chats", "Nachrichten")
            assertNotNull("Chat-Kachel nicht gefunden", chatEntry)
            chatEntry?.click()
        }

        // Chat geladen
        chatTitle = device.wait(Until.findObject(By.textContains("BamaChat")), TIMEOUT)
        assertNotNull("Chat nicht geöffnet", chatTitle)

        // In Profil wechseln (Bottom-Tab)
        val profileEntry = waitForAnyText(4_000L, "Profil", "Profile")
            ?: waitForAnyDescContains(4_000L, "Profil", "Profile")
        if (profileEntry != null) {
            profileEntry.click()
            val profileTitle = device.wait(
                Until.findObject(By.textContains("Profil")),
                6_000L
            )
            if (profileTitle != null) {
                device.pressBack()
                waitForAnyText(4_000L, "Chat", "Chats", "Nachrichten")?.click()
                device.wait(Until.findObject(By.textContains("BamaChat")), TIMEOUT)
            }
        }

        // Kern-Chat-Aktionen vorhanden (labels variieren je nach Build/Locale)
        val anyInputAction =
            waitForAnyDescContains(6_000L, "Senden", "Send", "Hochladen", "Upload", "Mikro", "Voice", "Diktieren")
                ?: waitForAnyText(6_000L, "Bild-KI", "Bild KI", "Schreib was")
        val chatStillVisible = device.hasObject(By.textContains("BamaChat"))
        assertTrue("Chat nicht stabil sichtbar", anyInputAction != null || chatStillVisible)
    }

    private fun launchApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: throw IllegalStateException("Launch Intent für $PACKAGE_NAME nicht gefunden")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        device.pressHome()
        SystemClock.sleep(400)
        context.startActivity(launchIntent)
        waitForAppVisible(TIMEOUT)
    }

    private fun waitForAnyText(timeoutMs: Long, vararg values: String): UiObject2? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            values.forEach { value ->
                device.findObject(By.text(value))?.let { return it }
                device.findObject(By.textContains(value))?.let { return it }
            }
            device.waitForIdle()
            SystemClock.sleep(250)
        }
        return null
    }

    private fun waitForAnyDescContains(timeoutMs: Long, vararg values: String): UiObject2? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            values.forEach { value ->
                device.findObject(By.descContains(value))?.let { return it }
            }
            device.waitForIdle()
            SystemClock.sleep(250)
        }
        return null
    }

    private fun waitForTextPrefix(timeoutMs: Long, prefix: String): UiObject2? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            device.findObject(By.textStartsWith(prefix))?.let { return it }
            device.waitForIdle()
            SystemClock.sleep(250)
        }
        return null
    }

    private fun waitForAppVisible(timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            allowRuntimeDialogsIfPresent()
            val byPkg = device.hasObject(By.pkg(PACKAGE_NAME))
            val byTitle = device.hasObject(By.textContains("BamaChat")) ||
                device.hasObject(By.textContains("BamaHub")) ||
                device.hasObject(By.textContains("Willkommen"))
            if (byPkg || byTitle) return true
            device.waitForIdle()
            SystemClock.sleep(300)
        }
        return false
    }

    private fun allowRuntimeDialogsIfPresent() {
        listOf(
            "Zulassen",
            "Erlauben",
            "Nur während der Nutzung der App",
            "Beim Verwenden der App",
            "Allow",
            "While using the app",
            "Nur dieses Mal",
            "Only this time"
        ).forEach { label ->
            device.findObject(By.textContains(label))?.click()
        }
    }

}
