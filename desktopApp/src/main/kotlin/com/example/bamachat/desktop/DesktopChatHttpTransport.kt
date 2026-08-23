package com.example.bamachat.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import java.io.Closeable
import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

internal class DesktopChatHttpResponse(
    val statusCode: Int,
    val body: InputStream
) : Closeable {
    override fun close() {
        body.close()
    }
}

internal interface DesktopChatHttpTransport {
    suspend fun execute(request: HttpRequest): DesktopChatHttpResponse
}

internal class JdkDesktopChatHttpTransport(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()
) : DesktopChatHttpTransport {
    override suspend fun execute(request: HttpRequest): DesktopChatHttpResponse {
        val future = httpClient.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofInputStream()
        )
        val response = try {
            future.await()
        } catch (cancelled: CancellationException) {
            future.cancel(true)
            future.whenComplete { lateResponse, _ ->
                runCatching { lateResponse?.body()?.close() }
            }
            throw cancelled
        } catch (failure: Throwable) {
            throw failure.unwrapCompletion()
        }
        try {
            currentCoroutineContext().ensureActive()
        } catch (cancelled: CancellationException) {
            runCatching { response.body().close() }
            throw cancelled
        }
        return DesktopChatHttpResponse(
            statusCode = response.statusCode(),
            body = response.body()
        )
    }

    private fun Throwable.unwrapCompletion(): Throwable {
        var current = this
        while (
            (current is CompletionException || current is ExecutionException) &&
            current.cause != null
        ) {
            current = requireNotNull(current.cause)
        }
        return current
    }
}
