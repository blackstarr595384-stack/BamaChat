package com.example.bamachat

import android.content.Context
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.ChatMessageEntity
import com.example.bamachat.data.local.ConversationEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatLongHistoryBenchmarkTest {

    companion object {
        private const val TIMEOUT = 25_000L
    }

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun longHistoryChat_scrollBenchmark() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val benchmarkConversationId = "benchmark-long-history-1200"
        val preferences = targetContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val previousConversationId = preferences.getString("current_conversation_id", null)

        try {
            seedConversation(targetContext, benchmarkConversationId, 1_200)

            device.pressHome()
            SystemClock.sleep(400)
            executeShell("dumpsys gfxinfo ${TestAppIdentity.APPLICATION_ID} reset")
            launchApp()

            clickAnyText("Als Gast starten", "Als Gast fortfahren", "Zum BamaHub")
            if (!device.hasObject(By.textContains("BamaChat"))) {
                clickAnyText("Chat", "Chats", "Nachrichten")
            }
            val chatVisible = device.wait(Until.hasObject(By.textContains("BamaChat")), TIMEOUT)
            assertTrue("Chat-Screen wurde nicht geöffnet", chatVisible)

            repeat(22) { index ->
                if (index % 2 == 0) swipeUp() else swipeDown()
            }
            device.waitForIdle()
            SystemClock.sleep(600)

            val gfxOutput = executeShell("dumpsys gfxinfo ${TestAppIdentity.APPLICATION_ID}")
            val totalFrames = Regex("""Total frames rendered:\s+(\d+)""")
                .find(gfxOutput)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0

            val jankPercent = Regex("""Janky frames:\s+\d+\s+\(([\d.]+)%\)""")
                .find(gfxOutput)
                ?.groupValues
                ?.getOrNull(1)
                ?.toFloatOrNull()
                ?: -1f

            println("CHAT_BENCHMARK totalFrames=$totalFrames jankPercent=$jankPercent")
            assertTrue("Scroll-Benchmark lieferte keine Frames", totalFrames > 0)
            assertTrue("Jank-Wert konnte nicht gelesen werden", jankPercent >= 0f)
        } finally {
            device.pressHome()
            cleanupBenchmarkConversation(targetContext, benchmarkConversationId)
            preferences.edit().apply {
                if (previousConversationId == null) {
                    remove("current_conversation_id")
                } else {
                    putString("current_conversation_id", previousConversationId)
                }
            }.commit()
        }
    }

    private suspend fun seedConversation(context: Context, conversationId: String, messageCount: Int) {
        val dao = ChatDatabase.getDatabase(context).chatDao()
        dao.deleteMessagesForConversation(conversationId)
        dao.deleteMessagesFtsForConversation(conversationId)
        dao.deleteConversation(conversationId)

        val now = System.currentTimeMillis()
        dao.insertConversation(
            ConversationEntity(
                id = conversationId,
                title = "Benchmark Chat",
                createdAt = now,
                updatedAt = now,
                personaName = "ASSISTANT"
            )
        )

        val baseTs = now - messageCount * 1_000L
        for (index in 1..messageCount) {
            dao.insertMessage(
                ChatMessageEntity(
                    id = "bench-$index",
                    conversationId = conversationId,
                    text = if (index % 2 == 0) {
                        "User message $index - benchmark lorem ipsum dolor sit amet."
                    } else {
                        "Assistant message $index - benchmark response with context and detail."
                    },
                    isUser = index % 2 == 0,
                    timestamp = baseTs + index * 1_000L
                )
            )
        }

        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("current_conversation_id", conversationId)
            .apply()
    }

    private fun launchApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = TestAppIdentity.mainActivityIntent()
        device.pressHome()
        SystemClock.sleep(350)
        context.startActivity(launchIntent)
    }

    private suspend fun cleanupBenchmarkConversation(context: Context, conversationId: String) {
        val dao = ChatDatabase.getDatabase(context).chatDao()
        dao.deleteMessagesForConversation(conversationId)
        dao.deleteMessagesFtsForConversation(conversationId)
        dao.deleteConversation(conversationId)
    }

    private fun clickAnyText(vararg values: String): Boolean {
        val end = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < end) {
            values.forEach { value ->
                val exact = device.findObject(By.text(value))
                if (exact != null) {
                    exact.click()
                    return true
                }
                val contains = device.findObject(By.textContains(value))
                if (contains != null) {
                    contains.click()
                    return true
                }
            }
            device.waitForIdle()
            SystemClock.sleep(200)
        }
        return false
    }

    private fun swipeUp() {
        val x = device.displayWidth / 2
        val fromY = (device.displayHeight * 0.82f).toInt()
        val toY = (device.displayHeight * 0.24f).toInt()
        device.swipe(x, fromY, x, toY, 28)
        device.waitForIdle()
        SystemClock.sleep(120)
    }

    private fun swipeDown() {
        val x = device.displayWidth / 2
        val fromY = (device.displayHeight * 0.24f).toInt()
        val toY = (device.displayHeight * 0.82f).toInt()
        device.swipe(x, fromY, x, toY, 28)
        device.waitForIdle()
        SystemClock.sleep(120)
    }

    private fun executeShell(command: String): String {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pfd = uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }
}
