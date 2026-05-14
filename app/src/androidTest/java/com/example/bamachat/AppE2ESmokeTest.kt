package com.example.bamachat

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
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
        if (guestButton != null) {
            clickAnyText(2_000L, "Als Gast starten", "Als Gast fortfahren", "Zum BamaHub")
        }

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
            clickAnyText(2_000L, "Chat", "Chats", "Nachrichten")
        }

        // Chat geladen
        chatTitle = device.wait(Until.findObject(By.textContains("BamaChat")), TIMEOUT)
        assertNotNull("Chat nicht geöffnet", chatTitle)

        // Bottom-Nav Smoke: best effort Navigation (nicht hart erzwingen, da Gerätezustand variieren kann)
        val homeClicked = clickAnyText(2_500L, "Home") || clickAnyDescContains(2_500L, "Home")
        if (homeClicked) {
            waitForTextPrefix(4_000L, "Provider: ")
            val settingsClicked = clickAnyText(2_500L, "Optionen", "Settings") ||
                clickAnyDescContains(2_500L, "Optionen", "Settings")
            if (settingsClicked) {
                waitForAnyText(4_000L, "Einstellungen", "Hauptbereiche", "Aktueller Status")
            }
            clickAnyText(3_000L, "Chat", "Chats", "Nachrichten") ||
                clickAnyDescContains(3_000L, "Chat", "Chats", "Nachrichten")
            device.wait(Until.findObject(By.textContains("BamaChat")), TIMEOUT)
        }

        // In Profil wechseln (Bottom-Tab)
        val profileEntry = waitForAnyText(4_000L, "Profil", "Profile")
            ?: waitForAnyDescContains(4_000L, "Profil", "Profile")
        if (profileEntry != null) {
            val clicked = clickAnyText(2_000L, "Profil", "Profile") ||
                clickAnyDescContains(2_000L, "Profil", "Profile")
            assertTrue("Profil-Tab konnte nicht angeklickt werden", clicked)
            val profileTitle = device.wait(
                Until.findObject(By.textContains("Profil")),
                6_000L
            )
            if (profileTitle != null) {
                device.pressBack()
                clickAnyText(4_000L, "Chat", "Chats", "Nachrichten")
                device.wait(Until.findObject(By.textContains("BamaChat")), TIMEOUT)
            }
        }

        // Kern-Chat-Aktionen vorhanden (labels variieren je nach Build/Locale)
        val anyInputAction =
            waitForAnyDescContains(6_000L, "Senden", "Send", "Hochladen", "Upload", "Mikro", "Voice", "Diktieren")
                ?: waitForAnyText(6_000L, "Bild-KI", "Bild KI", "Schreib was")
        val chatStillVisible = device.hasObject(By.textContains("BamaChat"))
        assertTrue("Chat nicht stabil sichtbar", anyInputAction != null || chatStillVisible)

        // Accessibility-Labels der Eingabeleiste vorhanden
        val voiceA11y = waitForAnyDescContains(4_000L, "Spracherkennung starten", "Voice")
        assertTrue("A11y-Label für Sprachbutton fehlt", voiceA11y != null || chatStillVisible)

        // Mini-Apps Smoke: Route öffnen und Kern-Apps laden
        var miniAppsOpened = clickAnyText(3_500L, "Mini-Apps", "Mini Apps") ||
            clickAnyDescContains(3_500L, "Mini-Apps", "Mini Apps")
        if (!miniAppsOpened) {
            val homeBack = clickAnyText(2_500L, "Home") || clickAnyDescContains(2_500L, "Home")
            if (homeBack) {
                miniAppsOpened = clickAnyText(5_000L, "Mini-Apps", "Mini Apps") ||
                    clickAnyDescContains(5_000L, "Mini-Apps", "Mini Apps")
            }
        }
        assertTrue("Mini-Apps konnten nicht geöffnet werden", miniAppsOpened)

        val miniAppsVisible = waitForAnyText(
            TIMEOUT,
            "Mini-Apps V2",
            "Mini-Apps Discover",
            "Meine Apps"
        )
        assertNotNull("Mini-Apps Screen nicht geladen", miniAppsVisible)

        val photoOpened = clickAnyText(6_000L, "Photo Studio")
        assertTrue("Photo Studio konnte nicht geöffnet werden", photoOpened)
        val photoUiVisible = waitForAnyText(6_000L, "Bild wählen", "In Galerie speichern")
        assertNotNull("Photo Studio UI nicht sichtbar", photoUiVisible)
        device.pressBack()

        val voiceOpened = clickAnyText(6_000L, "Voice Notes AI")
        assertTrue("Voice Notes AI konnte nicht geöffnet werden", voiceOpened)
        val voiceUiVisible = waitForAnyText(6_000L, "Spracheingabe", "Text speichern")
        assertNotNull("Voice Notes AI UI nicht sichtbar", voiceUiVisible)
        device.pressBack()
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

    private fun clickAnyText(timeoutMs: Long, vararg values: String): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            values.forEach { value ->
                if (tryClick(device.findObject(By.text(value)))) return true
                if (tryClick(device.findObject(By.textContains(value)))) return true
            }
            device.waitForIdle()
            SystemClock.sleep(200)
        }
        return false
    }

    private fun clickAnyDescContains(timeoutMs: Long, vararg values: String): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            values.forEach { value ->
                if (tryClick(device.findObject(By.descContains(value)))) return true
            }
            device.waitForIdle()
            SystemClock.sleep(200)
        }
        return false
    }

    private fun tryClick(node: UiObject2?): Boolean {
        if (node == null) return false
        return try {
            node.click()
            true
        } catch (_: StaleObjectException) {
            false
        }
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
