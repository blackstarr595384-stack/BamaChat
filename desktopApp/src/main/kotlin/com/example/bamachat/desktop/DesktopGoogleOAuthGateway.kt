package com.example.bamachat.desktop

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DesktopGoogleOAuthGateway(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()
) {
    suspend fun fetchGoogleIdToken(
        clientId: String,
        clientSecret: String
    ): String = withContext(Dispatchers.IO) {
        val cleanClientId = clientId.trim()
        if (cleanClientId.isBlank()) {
            throw IllegalStateException("Google OAuth Client-ID fehlt.")
        }

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateState()
        val callbackPort = reservePort()
        val redirectUri = "http://127.0.0.1:$callbackPort/oauth2callback"

        val authUrl = buildAuthorizationUrl(
            clientId = cleanClientId,
            redirectUri = redirectUri,
            state = state,
            codeChallenge = codeChallenge
        )

        val authCode = awaitAuthorizationCode(
            authUrl = authUrl,
            redirectUri = redirectUri,
            expectedState = state,
            port = callbackPort
        )

        val response = exchangeCodeForTokens(
            clientId = cleanClientId,
            clientSecret = clientSecret.trim(),
            redirectUri = redirectUri,
            codeVerifier = codeVerifier,
            code = authCode
        )

        return@withContext response.getString("id_token")
            ?: throw IllegalStateException("Google OAuth lieferte kein id_token.")
    }

    private fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String
    ): String {
        val params = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to "openid email profile",
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "prompt" to "select_account"
        )
        val query = params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        return "https://accounts.google.com/o/oauth2/v2/auth?$query"
    }

    private fun awaitAuthorizationCode(
        authUrl: String,
        redirectUri: String,
        expectedState: String,
        port: Int
    ): String {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw IllegalStateException("Desktop-Browser-Integration ist nicht verfügbar.")
        }

        val callbackFuture = CompletableFuture<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/oauth2callback") { exchange ->
            val params = parseQuery(exchange.requestURI.rawQuery.orEmpty())
            val error = params["error"]
            val state = params["state"]
            val code = params["code"]

            val (statusCode, html) = when {
                !error.isNullOrBlank() -> {
                    callbackFuture.completeExceptionally(
                        IllegalStateException("Google OAuth abgebrochen: $error")
                    )
                    400 to "<html><body><h2>BamaChat Login fehlgeschlagen.</h2><p>Du kannst dieses Fenster schließen.</p></body></html>"
                }
                state != expectedState -> {
                    callbackFuture.completeExceptionally(
                        IllegalStateException("Google OAuth state mismatch.")
                    )
                    400 to "<html><body><h2>BamaChat Login fehlgeschlagen.</h2><p>Ungueltige Antwort.</p></body></html>"
                }
                code.isNullOrBlank() -> {
                    callbackFuture.completeExceptionally(
                        IllegalStateException("Google OAuth lieferte keinen Code.")
                    )
                    400 to "<html><body><h2>BamaChat Login fehlgeschlagen.</h2><p>Kein Code erhalten.</p></body></html>"
                }
                else -> {
                    callbackFuture.complete(code)
                    200 to "<html><body><h2>Login erfolgreich.</h2><p>Du kannst dieses Fenster schließen und zu BamaChat zurückkehren.</p></body></html>"
                }
            }

            val responseBytes = html.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { output ->
                output.write(responseBytes)
            }
        }

        server.start()
        try {
            Desktop.getDesktop().browse(URI(authUrl))
            return callbackFuture.get(180, TimeUnit.SECONDS)
        } catch (t: TimeoutException) {
            throw IllegalStateException("Google OAuth Timeout: Keine Antwort im Browser-Flow erhalten.")
        } finally {
            server.stop(0)
        }
    }

    private fun exchangeCodeForTokens(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        codeVerifier: String,
        code: String
    ): JsonObject {
        val fields = linkedMapOf(
            "client_id" to clientId,
            "code" to code,
            "code_verifier" to codeVerifier,
            "grant_type" to "authorization_code",
            "redirect_uri" to redirectUri
        ).apply {
            if (clientSecret.isNotBlank()) {
                put("client_secret", clientSecret)
            }
        }

        val body = fields.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        )
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Google Token-Exchange fehlgeschlagen: ${extractGoogleError(response.body())}")
        }
        return JsonParser.parseString(response.body()).asJsonObject
    }

    private fun extractGoogleError(body: String): String {
        return runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            val error = root.getString("error")
            val description = root.getString("error_description")
            listOfNotNull(error, description).joinToString(": ").ifBlank { body.take(280) }
        }.getOrElse { body.take(280) }
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val separator = part.indexOf('=')
                if (separator < 0) {
                    return@mapNotNull urlDecode(part) to ""
                }
                val key = urlDecode(part.substring(0, separator))
                val value = urlDecode(part.substring(separator + 1))
                key to value
            }
            .toMap()
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    private fun urlDecode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8)
    }

    private fun reservePort(): Int {
        return ServerSocket(0).use { socket -> socket.localPort }
    }

    private fun generateCodeVerifier(): String {
        val randomBytes = ByteArray(64)
        SecureRandom().nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
            .take(96)
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun generateState(): String {
        val randomBytes = ByteArray(18)
        SecureRandom().nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    private fun JsonObject.getString(key: String): String? {
        if (!has(key)) return null
        val value = get(key)
        if (value.isJsonNull) return null
        return value.asString
    }
}
