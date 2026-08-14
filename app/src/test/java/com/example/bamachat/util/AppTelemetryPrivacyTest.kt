package com.example.bamachat.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppTelemetryPrivacyTest {
    private val output = mutableListOf<String>()

    @Before
    fun setUp() {
        AppTelemetry.installTestSink(output::add)
    }

    @After
    fun tearDown() {
        AppTelemetry.installTestSink(null)
    }

    @Test
    fun sensitiveCanariesNeverReachTelemetryOutput() {
        val canaries = listOf(
            "uid-canary-123",
            "person-canary@example.invalid",
            "private search phrase canary",
            "private message canary",
            "api-key-canary-123",
            "token-canary-123"
        )

        AppTelemetry.setUserId(canaries[0])
        AppTelemetry.setCustomKey("email", canaries[1])
        AppTelemetry.log(canaries[3])
        AppTelemetry.logEvent(
            "search_performed",
            mapOf(
                "query" to canaries[2],
                "message" to canaries[3],
                "api_key" to canaries[4],
                "access_token" to canaries[5],
                "results_count" to 7
            )
        )
        AppTelemetry.logError("provider_error", IllegalStateException(canaries.joinToString()))

        val rendered = output.joinToString("\n")
        canaries.forEach { canary -> assertFalse(rendered.contains(canary)) }
        assertTrue(rendered.contains("event=search_performed"))
        assertTrue(rendered.contains("results_count=7"))
        assertTrue(rendered.contains("type=IllegalStateException"))
    }

    @Test
    fun technicalCountersAndCategoriesRemainUseful() {
        AppTelemetry.logEvent(
            "search_performed",
            mapOf(
                "input_length_bucket" to 16,
                "results_count" to 3,
                "filter_user_only" to true,
                "sort_type" to TestSort.RECENT
            )
        )

        val rendered = output.single()
        assertTrue(rendered.contains("input_length_bucket=16"))
        assertTrue(rendered.contains("results_count=3"))
        assertTrue(rendered.contains("filter_user_only=true"))
        assertTrue(rendered.contains("sort_type=RECENT"))
    }

    private enum class TestSort { RECENT }
}
