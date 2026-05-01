package com.example.bamachat.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartFeatureManagerTest {

    @Test
    fun testExtractUrl() {
        val regex = """(https?://[\w-]+(\.[\w-]+)+(/[\w ./?%&=-]*)?)""".toRegex()
        val text = "Check out https://www.google.com/search?q=android for info"
        val match = regex.find(text)
        assertEquals("https://www.google.com/search?q=android", match?.value)
    }

    @Test
    fun testExtractUrlWithSpecialChars() {
        val regex = """(https?://[\w-]+(\.[\w-]+)+(/[\w ./?%&=-]*)?)""".toRegex()
        val text = "Link: https://example.com/path-with-dash/and.dot?query=1&other=2"
        val match = regex.find(text)
        assertEquals("https://example.com/path-with-dash/and.dot?query=1&other=2", match?.value)
    }
}
