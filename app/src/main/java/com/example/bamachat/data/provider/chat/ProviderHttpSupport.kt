package com.example.bamachat.data.provider.chat

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer

internal object ProviderHttpSupport {
    const val MAX_RESPONSE_CHARS = 2_000_000
    const val MAX_LINE_CHARS = 256_000

    fun client(timeoutMs: Long): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun execute(call: Call): Response = withContext(Dispatchers.IO) {
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute()
        } catch (error: CancellationException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw ProviderChatException(ProviderChatError.TIMEOUT, message = "Provider timeout")
        } catch (error: SSLException) {
            throw ProviderChatException(ProviderChatError.TLS_FAILURE, message = "Provider TLS failure")
        } catch (error: IOException) {
            if (call.isCanceled()) throw CancellationException("Provider request cancelled")
            throw ProviderChatException(ProviderChatError.CONNECTION_FAILED, message = "Provider connection failed")
        } finally {
            handle?.dispose()
        }
    }

    fun ensureSuccess(response: Response) {
        val code = response.code
        if (code in 300..399) throw ProviderChatException(ProviderChatError.REDIRECT_BLOCKED, code, "Provider redirect blocked")
        if (response.isSuccessful) return
        val error = when (code) {
            400 -> ProviderChatError.BAD_REQUEST
            401, 403 -> ProviderChatError.AUTHENTICATION_FAILED
            404 -> ProviderChatError.NOT_FOUND
            408 -> ProviderChatError.TIMEOUT
            429 -> ProviderChatError.RATE_LIMITED
            in 400..499 -> ProviderChatError.HTTP_CLIENT_ERROR
            else -> ProviderChatError.HTTP_SERVER_ERROR
        }
        throw ProviderChatException(error, code, "Provider HTTP failure")
    }

    fun appendBounded(builder: StringBuilder, text: String) {
        if (builder.length + text.length > MAX_RESPONSE_CHARS) {
            throw ProviderChatException(ProviderChatError.RESPONSE_TOO_LARGE, message = "Provider response too large")
        }
        builder.append(text)
    }

    fun readBounded(body: ResponseBody): String {
        val source = body.source()
        val buffer = Buffer()
        var totalBytes = 0L
        val maxBytes = MAX_RESPONSE_CHARS.toLong() * 4L
        while (true) {
            val read = source.read(buffer, minOf(8_192L, maxBytes + 1L - totalBytes))
            if (read == -1L) break
            totalBytes += read
            if (totalBytes > maxBytes) {
                throw ProviderChatException(ProviderChatError.RESPONSE_TOO_LARGE, message = "Provider response too large")
            }
        }
        val text = buffer.readUtf8()
        if (text.length > MAX_RESPONSE_CHARS) {
            throw ProviderChatException(ProviderChatError.RESPONSE_TOO_LARGE, message = "Provider response too large")
        }
        return text
    }
}
