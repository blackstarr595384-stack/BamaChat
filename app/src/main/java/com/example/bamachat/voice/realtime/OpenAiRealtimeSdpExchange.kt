package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.VoiceDiagnostics
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

internal const val OPENAI_REALTIME_CALLS_URL = "https://api.openai.com/v1/realtime/calls"
internal const val MAX_SDP_CHARS = 512_000
private val SDP_MEDIA_TYPE = "application/sdp".toMediaType()

internal fun interface RealtimeSdpAnswerExchange {
    suspend fun exchange(offerSdp: String, clientSecret: String): String
}

internal class OpenAiRealtimeSdpExchange(
    private val callFactory: Call.Factory,
    private val diagnostics: VoiceDiagnostics
) : RealtimeSdpAnswerExchange {
    override suspend fun exchange(offerSdp: String, clientSecret: String): String =
        suspendCancellableCoroutine { continuation ->
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "sdp",
                    "offer.sdp",
                    offerSdp.toByteArray(StandardCharsets.UTF_8).toRequestBody(SDP_MEDIA_TYPE)
                )
                .build()
            val request = Request.Builder()
                .url(OPENAI_REALTIME_CALLS_URL)
                .header("Authorization", "Bearer $clientSecret")
                .header("Accept", "application/sdp")
                .post(multipartBody)
                .build()
            val call = callFactory.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(failure(RealtimeSdpErrorCategory.NETWORK))
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (it.code != HTTP_CREATED) {
                                throw failure(categoryForStatus(it.code), it.code)
                            }
                            val responseBody = it.body
                                ?: throw failure(RealtimeSdpErrorCategory.EMPTY_RESPONSE, it.code)
                            val contentLength = responseBody.contentLength()
                            if (contentLength > MAX_SDP_CHARS) {
                                throw failure(
                                    RealtimeSdpErrorCategory.RESPONSE_TOO_LARGE,
                                    it.code
                                )
                            }
                            responseBody.charStream().use(::readBoundedSdp)
                                .takeIf(String::isNotBlank)
                                ?: throw failure(
                                    RealtimeSdpErrorCategory.EMPTY_RESPONSE,
                                    it.code
                                )
                        }
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }

    private fun readBoundedSdp(reader: java.io.Reader): String {
        val result = StringBuilder()
        val buffer = CharArray(8_192)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            if (result.length + read > MAX_SDP_CHARS) {
                throw failure(RealtimeSdpErrorCategory.RESPONSE_TOO_LARGE, HTTP_CREATED)
            }
            result.append(buffer, 0, read)
        }
        return result.toString()
    }

    private fun failure(
        category: RealtimeSdpErrorCategory,
        httpStatus: Int? = null
    ): RealtimeSdpExchangeException {
        diagnostics.event(
            "voice_realtime_transport_failure",
            mapOf(
                "phase" to "sdp_exchange",
                "http_status" to (httpStatus?.toString() ?: "none"),
                "category" to category.telemetryValue
            )
        )
        return RealtimeSdpExchangeException(category, httpStatus)
    }

    private fun categoryForStatus(status: Int): RealtimeSdpErrorCategory = when (status) {
        400 -> RealtimeSdpErrorCategory.REQUEST_REJECTED
        401, 403 -> RealtimeSdpErrorCategory.AUTHENTICATION
        429 -> RealtimeSdpErrorCategory.RATE_LIMITED
        in 500..599 -> RealtimeSdpErrorCategory.PROVIDER_UNAVAILABLE
        else -> RealtimeSdpErrorCategory.UNEXPECTED_STATUS
    }

    private companion object {
        const val HTTP_CREATED = 201
    }
}

internal enum class RealtimeSdpErrorCategory(val telemetryValue: String) {
    NETWORK("network"),
    REQUEST_REJECTED("request_rejected"),
    AUTHENTICATION("authentication"),
    RATE_LIMITED("rate_limited"),
    PROVIDER_UNAVAILABLE("provider_unavailable"),
    UNEXPECTED_STATUS("unexpected_status"),
    EMPTY_RESPONSE("empty_response"),
    RESPONSE_TOO_LARGE("response_too_large")
}

internal class RealtimeSdpExchangeException(
    val category: RealtimeSdpErrorCategory,
    val httpStatus: Int?
) : IOException("sdp_exchange_${category.telemetryValue}") {
    val recoverable: Boolean = true
}

internal enum class RealtimeSdpDescriptionType {
    OFFER,
    ANSWER
}

internal data class RealtimeSdpDescription(
    val type: RealtimeSdpDescriptionType,
    val sdp: String
)

internal interface RealtimeSdpHandshakePeer {
    suspend fun createOffer(): RealtimeSdpDescription
    suspend fun setLocalDescription(description: RealtimeSdpDescription)
    suspend fun setRemoteDescription(description: RealtimeSdpDescription)
}

internal suspend fun performRealtimeSdpHandshake(
    peer: RealtimeSdpHandshakePeer,
    exchange: RealtimeSdpAnswerExchange,
    clientSecret: String
) {
    val offer = peer.createOffer()
    check(offer.type == RealtimeSdpDescriptionType.OFFER)
    peer.setLocalDescription(offer)
    val answerSdp = exchange.exchange(offer.sdp, clientSecret)
    peer.setRemoteDescription(
        RealtimeSdpDescription(RealtimeSdpDescriptionType.ANSWER, answerSdp)
    )
}

internal class RealtimeConnectionReadinessGate(
    private val onReady: () -> Unit
) {
    private var peerConnected = false
    private var dataChannelOpen = false
    private var ready = false

    @Synchronized
    fun markPeerConnected() {
        peerConnected = true
        completeIfReady()
    }

    @Synchronized
    fun markDataChannelOpen() {
        dataChannelOpen = true
        completeIfReady()
    }

    private fun completeIfReady() {
        if (!ready && peerConnected && dataChannelOpen) {
            ready = true
            onReady()
        }
    }
}
