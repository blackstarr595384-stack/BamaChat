package com.example.bamachat.data.provider.discovery

import com.example.bamachat.data.provider.chat.ProviderChatError
import com.example.bamachat.data.provider.chat.ProviderChatException
import com.example.bamachat.data.provider.chat.ProviderHttpSupport
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.Request

internal object ProviderDiscoveryHttp {
    const val MAX_MODELS = 500
    const val MAX_MODEL_ID_LENGTH = 200

    suspend fun get(request: Request, timeoutMs: Long): JsonElement {
        val call = ProviderHttpSupport.client(timeoutMs)
            .newBuilder()
            .retryOnConnectionFailure(false)
            .build()
            .newCall(request)
        val callerJob = currentCoroutineContext()[Job]
        return withContext(Dispatchers.IO) {
            val cancellationHandle = callerJob?.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }
            try {
                call.execute().use { response ->
                    ProviderHttpSupport.ensureSuccess(response)
                    val body = response.body ?: throw ProviderDiscoveryException(
                        ProviderDiscoveryError.EMPTY_RESPONSE,
                        "Discovery response empty"
                    )
                    val raw = ProviderHttpSupport.readBounded(body)
                    if (raw.isBlank()) throw ProviderDiscoveryException(
                        ProviderDiscoveryError.EMPTY_RESPONSE,
                        "Discovery response empty"
                    )
                    runCatching { JsonParser.parseString(raw) }
                        .getOrElse { throw ProviderDiscoveryException(ProviderDiscoveryError.INVALID_JSON, "Discovery response invalid") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProviderDiscoveryException) {
                throw error
            } catch (error: ProviderChatException) {
                throw ProviderDiscoveryException(error.toDiscoveryError(), "Discovery request failed")
            } catch (_: SocketTimeoutException) {
                throw ProviderDiscoveryException(ProviderDiscoveryError.TIMEOUT, "Discovery request timed out")
            } catch (_: SSLException) {
                throw ProviderDiscoveryException(ProviderDiscoveryError.TLS_FAILURE, "Discovery TLS failure")
            } catch (_: IOException) {
                if (call.isCanceled()) throw CancellationException("Discovery request cancelled")
                throw ProviderDiscoveryException(ProviderDiscoveryError.CONNECTION_FAILED, "Discovery connection failed")
            } finally {
                cancellationHandle?.dispose()
            }
        }
    }

    fun normalizeModels(rawIds: Sequence<String>): Pair<List<DiscoveredProviderModel>, Boolean> {
        val unique = linkedSetOf<String>()
        var truncated = false
        rawIds.forEach { raw ->
            val id = raw.trim()
            if (id.isEmpty() || id.length > MAX_MODEL_ID_LENGTH || id in unique) return@forEach
            if (unique.size >= MAX_MODELS) {
                truncated = true
                return@forEach
            }
            unique += id
        }
        return unique.map(::DiscoveredProviderModel) to truncated
    }

    private fun ProviderChatException.toDiscoveryError(): ProviderDiscoveryError = when (error) {
        ProviderChatError.BAD_REQUEST,
        ProviderChatError.HTTP_CLIENT_ERROR -> ProviderDiscoveryError.BAD_REQUEST
        ProviderChatError.AUTHENTICATION_FAILED -> ProviderDiscoveryError.AUTHENTICATION_FAILED
        ProviderChatError.NOT_FOUND -> ProviderDiscoveryError.NOT_FOUND
        ProviderChatError.TIMEOUT -> ProviderDiscoveryError.TIMEOUT
        ProviderChatError.RATE_LIMITED -> ProviderDiscoveryError.RATE_LIMITED
        ProviderChatError.HTTP_SERVER_ERROR -> ProviderDiscoveryError.SERVER_ERROR
        ProviderChatError.CONNECTION_FAILED -> ProviderDiscoveryError.CONNECTION_FAILED
        ProviderChatError.TLS_FAILURE -> ProviderDiscoveryError.TLS_FAILURE
        ProviderChatError.REDIRECT_BLOCKED -> ProviderDiscoveryError.REDIRECT_BLOCKED
        ProviderChatError.EMPTY_RESPONSE -> ProviderDiscoveryError.EMPTY_RESPONSE
        ProviderChatError.INVALID_RESPONSE -> ProviderDiscoveryError.INVALID_JSON
        ProviderChatError.RESPONSE_TOO_LARGE -> ProviderDiscoveryError.RESPONSE_TOO_LARGE
        else -> ProviderDiscoveryError.CONNECTION_FAILED
    }
}
