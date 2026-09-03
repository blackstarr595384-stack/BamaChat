package com.example.bamachat.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ImageUrlResolver(
    private val httpClient: OkHttpClient = defaultClient()
) {
    suspend fun resolveFirstWorkingUrl(candidates: List<String>): String? = withContext(Dispatchers.IO) {
        candidates.firstOrNull { candidate ->
            isReachable(candidate)
        }
    }

    fun shutdown() {
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
    }

    private fun isReachable(candidate: String): Boolean {
        return runCatching {
            val request = Request.Builder().url(candidate).get().build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()
    }
}
