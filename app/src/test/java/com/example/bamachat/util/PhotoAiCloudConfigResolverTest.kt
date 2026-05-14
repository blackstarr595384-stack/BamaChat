package com.example.bamachat.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAiCloudConfigResolverTest {

    @Test
    fun deriveFromCloudFunctionsEndpointRewritesFunctionName() {
        val source = "https://europe-west1-example.cloudfunctions.net/webSearch"
        val resolved = PhotoAiCloudConfigResolver.deriveFromLiveWebEndpoint(source)
        assertEquals(
            "https://europe-west1-example.cloudfunctions.net/photoEdit",
            resolved
        )
    }

    @Test
    fun deriveFromRunAppEndpointRewritesServicePrefix() {
        val source = "https://websearch-abc123-ew.a.run.app"
        val resolved = PhotoAiCloudConfigResolver.deriveFromLiveWebEndpoint(source)
        assertEquals("https://photoedit-abc123-ew.a.run.app", resolved)
    }

    @Test
    fun deriveReturnsBlankForUnknownPattern() {
        val source = "https://example.com/api/search"
        val resolved = PhotoAiCloudConfigResolver.deriveFromLiveWebEndpoint(source)
        assertEquals("", resolved)
    }
}
