package com.example.bamachat.voice.realtime

import com.example.bamachat.voice.VoiceDiagnostics
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OpenAiRealtimeSdpExchangeTest {
    @Test
    fun requestUsesRealtimeCallsEndpointAndEphemeralBearerCredential() = runBlocking {
        val fixture = fixture()

        fixture.exchange.exchange(OFFER_SDP, EPHEMERAL_CLIENT_SECRET)

        val request = fixture.callFactory.request
        assertNotNull(request)
        assertEquals(OPENAI_REALTIME_CALLS_URL, request!!.url.toString())
        assertTrue(request.url.encodedPath.endsWith("/v1/realtime/calls"))
        assertEquals("Bearer $EPHEMERAL_CLIENT_SECRET", request.header("Authorization"))
        assertEquals("application/sdp", request.header("Accept"))
    }

    @Test
    fun requestContainsExactlyOneMultipartSdpPart() = runBlocking {
        val fixture = fixture()

        fixture.exchange.exchange(OFFER_SDP, EPHEMERAL_CLIENT_SECRET)

        val body = fixture.callFactory.request?.body as MultipartBody
        assertEquals("multipart/form-data", body.type.toString())
        assertEquals(1, body.parts.size)
        val part = body.parts.single()
        assertTrue(part.headers?.get("Content-Disposition").orEmpty().contains("name=\"sdp\""))
        assertTrue(part.headers?.get("Content-Disposition").orEmpty().contains("filename=\"offer.sdp\""))
        assertEquals("application/sdp", part.body.contentType().toString())
        val buffer = Buffer()
        part.body.writeTo(buffer)
        assertEquals(OFFER_SDP, buffer.readUtf8())
    }

    @Test
    fun http201ReturnsBoundedSdpAnswer() = runBlocking {
        val fixture = fixture(status = 201, responseBody = ANSWER_SDP)

        val answer = fixture.exchange.exchange(OFFER_SDP, EPHEMERAL_CLIENT_SECRET)

        assertEquals(ANSWER_SDP, answer)
        assertTrue(fixture.diagnostics.events.isEmpty())
    }

    @Test
    fun http400IsRecoverableAndLogsOnlySafeMetadata() = runBlocking {
        val privateProviderBody = "provider response must stay private"
        val fixture = fixture(status = 400, responseBody = privateProviderBody)

        val failure = fixture.captureFailure()

        assertEquals(RealtimeSdpErrorCategory.REQUEST_REJECTED, failure.category)
        assertEquals(400, failure.httpStatus)
        assertTrue(failure.recoverable)
        val diagnostics = fixture.diagnostics.serialized()
        assertTrue(diagnostics.contains("sdp_exchange"))
        assertTrue(diagnostics.contains("request_rejected"))
        assertTrue(diagnostics.contains("400"))
        assertFalse(diagnostics.contains(OFFER_SDP))
        assertFalse(diagnostics.contains(EPHEMERAL_CLIENT_SECRET))
        assertFalse(diagnostics.contains(privateProviderBody))
    }

    @Test
    fun http401And403AreHandledWithoutSensitiveDiagnostics() = runBlocking {
        for (status in listOf(401, 403)) {
            val fixture = fixture(status = status, responseBody = "private provider body")

            val failure = fixture.captureFailure()

            assertEquals(RealtimeSdpErrorCategory.AUTHENTICATION, failure.category)
            assertEquals(status, failure.httpStatus)
            assertFalse(fixture.diagnostics.serialized().contains(EPHEMERAL_CLIENT_SECRET))
            assertFalse(fixture.diagnostics.serialized().contains("private provider body"))
        }
    }

    @Test
    fun blankSdpAnswerIsRejected() = runBlocking {
        val fixture = fixture(status = 201, responseBody = "   ")

        val failure = fixture.captureFailure()

        assertEquals(RealtimeSdpErrorCategory.EMPTY_RESPONSE, failure.category)
    }

    @Test
    fun oversizedSdpAnswerIsRejected() = runBlocking {
        val fixture = fixture(status = 201, responseBody = "x".repeat(MAX_SDP_CHARS + 1))

        val failure = fixture.captureFailure()

        assertEquals(RealtimeSdpErrorCategory.RESPONSE_TOO_LARGE, failure.category)
    }

    @Test
    fun handshakeAwaitsOfferAndLocalDescriptionBeforeSettingRemoteAnswer() = runBlocking {
        val operations = mutableListOf<String>()
        var remoteDescription: RealtimeSdpDescription? = null
        val peer = object : RealtimeSdpHandshakePeer {
            override suspend fun createOffer(): RealtimeSdpDescription {
                operations += "create_offer"
                return RealtimeSdpDescription(RealtimeSdpDescriptionType.OFFER, OFFER_SDP)
            }

            override suspend fun setLocalDescription(description: RealtimeSdpDescription) {
                operations += "set_local_${description.type.name.lowercase()}"
            }

            override suspend fun setRemoteDescription(description: RealtimeSdpDescription) {
                operations += "set_remote_${description.type.name.lowercase()}"
                remoteDescription = description
            }
        }
        val exchange = RealtimeSdpAnswerExchange { offer, credential ->
            assertEquals(OFFER_SDP, offer)
            assertEquals(EPHEMERAL_CLIENT_SECRET, credential)
            operations += "exchange_sdp"
            ANSWER_SDP
        }

        performRealtimeSdpHandshake(peer, exchange, EPHEMERAL_CLIENT_SECRET)

        assertEquals(
            listOf("create_offer", "set_local_offer", "exchange_sdp", "set_remote_answer"),
            operations
        )
        assertEquals(RealtimeSdpDescriptionType.ANSWER, remoteDescription?.type)
        assertEquals(ANSWER_SDP, remoteDescription?.sdp)
    }

    @Test
    fun peerConnectedAndOpenDataChannelAreBothRequiredForReadiness() {
        var readyCalls = 0
        val gate = RealtimeConnectionReadinessGate { readyCalls++ }

        gate.markPeerConnected()
        assertEquals(0, readyCalls)
        gate.markDataChannelOpen()
        assertEquals(1, readyCalls)
        gate.markPeerConnected()
        gate.markDataChannelOpen()
        assertEquals(1, readyCalls)
    }

    private fun fixture(
        status: Int = 201,
        responseBody: String = ANSWER_SDP
    ): Fixture {
        val diagnostics = RecordingDiagnostics()
        val callFactory = RecordingCallFactory(status, responseBody)
        return Fixture(
            OpenAiRealtimeSdpExchange(callFactory, diagnostics),
            callFactory,
            diagnostics
        )
    }

    private suspend fun Fixture.captureFailure(): RealtimeSdpExchangeException {
        val failure = runCatching {
            exchange.exchange(OFFER_SDP, EPHEMERAL_CLIENT_SECRET)
        }.exceptionOrNull()
        assertTrue(failure is RealtimeSdpExchangeException)
        return failure as RealtimeSdpExchangeException
    }

    private data class Fixture(
        val exchange: OpenAiRealtimeSdpExchange,
        val callFactory: RecordingCallFactory,
        val diagnostics: RecordingDiagnostics
    )

    private class RecordingDiagnostics : VoiceDiagnostics {
        val events = mutableListOf<Pair<String, Map<String, String>>>()

        override fun event(name: String, attributes: Map<String, String>) {
            events += name to attributes
        }

        override fun timing(name: String, durationMs: Long, attributes: Map<String, String>) = Unit

        fun serialized(): String = events.joinToString(separator = "|") { (name, attributes) ->
            "$name:${attributes.entries.sortedBy(Map.Entry<String, String>::key)}"
        }
    }

    private class RecordingCallFactory(
        private val status: Int,
        private val responseBody: String
    ) : Call.Factory {
        var request: Request? = null

        override fun newCall(request: Request): Call {
            this.request = request
            return FakeCall(request, status, responseBody)
        }
    }

    private class FakeCall(
        private val request: Request,
        private val status: Int,
        private val responseBody: String
    ) : Call {
        private var executed = false
        private var canceled = false

        override fun request(): Request = request

        override fun execute(): Response {
            executed = true
            return response()
        }

        override fun enqueue(responseCallback: Callback) {
            executed = true
            responseCallback.onResponse(this, response())
        }

        override fun cancel() {
            canceled = true
        }

        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = FakeCall(request, status, responseBody)

        private fun response(): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("test")
            .body(responseBody.toResponseBody("application/sdp".toMediaType()))
            .build()
    }

    private companion object {
        const val EPHEMERAL_CLIENT_SECRET = "ephemeral-client-credential"
        const val OFFER_SDP = "v=0\r\no=local-offer\r\n"
        const val ANSWER_SDP = "v=0\r\no=remote-answer\r\n"
    }
}
