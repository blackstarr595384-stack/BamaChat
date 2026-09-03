package com.example.bamachat.service

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlResolverTest {
    @Test
    fun resolveFirstWorkingUrlReturnsNullForEmptyList() = runBlocking {
        val resolver = ImageUrlResolver(clientReturningCodes())

        assertNull(resolver.resolveFirstWorkingUrl(emptyList()))
    }

    @Test
    fun resolveFirstWorkingUrlReturnsFirstSuccessfulCandidate() = runBlocking {
        val resolver = ImageUrlResolver(
            clientReturningCodes(
                "https://img.example/broken.png" to 404,
                "https://img.example/ok.png" to 200
            )
        )

        assertEquals(
            "https://img.example/ok.png",
            resolver.resolveFirstWorkingUrl(
                listOf(
                    "https://img.example/broken.png",
                    "https://img.example/ok.png"
                )
            )
        )
    }

    @Test
    fun resolveFirstWorkingUrlReturnsNullWhenNoCandidateWorks() = runBlocking {
        val resolver = ImageUrlResolver(
            clientReturningCodes(
                "https://img.example/a.png" to 500,
                "https://img.example/b.png" to 403
            )
        )

        assertNull(
            resolver.resolveFirstWorkingUrl(
                listOf(
                    "https://img.example/a.png",
                    "https://img.example/b.png"
                )
            )
        )
    }

    @Test
    fun resolveFirstWorkingUrlIgnoresBlankAndMalformedCandidates() = runBlocking {
        val resolver = ImageUrlResolver(
            clientReturningCodes(
                "https://img.example/ok.png" to 200
            )
        )

        assertEquals(
            "https://img.example/ok.png",
            resolver.resolveFirstWorkingUrl(
                listOf(
                    "",
                    "   ",
                    "not-a-url",
                    "https://img.example/ok.png"
                )
            )
        )
    }

    @Test
    fun resolveFirstWorkingUrlFallsBackToLaterSuccessfulCandidate() = runBlocking {
        val resolver = ImageUrlResolver(
            clientReturningCodes(
                "https://img.example/a.png" to 500,
                "https://img.example/b.png" to 404,
                "https://img.example/c.png" to 200
            )
        )

        assertEquals(
            "https://img.example/c.png",
            resolver.resolveFirstWorkingUrl(
                listOf(
                    "https://img.example/a.png",
                    "https://img.example/b.png",
                    "https://img.example/c.png"
                )
            )
        )
    }

    private fun clientReturningCodes(vararg responses: Pair<String, Int>): OkHttpClient {
        val codes = responses.toMap()
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val code = codes[chain.request().url.toString()] ?: 404
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()
    }
}
