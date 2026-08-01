package com.example.bamachat.data.github

import com.example.bamachat.shared.core.github.GitHubAnalysisArea
import com.example.bamachat.shared.core.github.GitHubReadIssue
import com.example.bamachat.shared.core.github.GitHubReadLimits
import com.example.bamachat.shared.core.github.GitHubReadOnlyRepositoryGateway
import com.example.bamachat.shared.core.github.GitHubReadResult
import com.example.bamachat.shared.core.github.GitHubRepositoryPolicy
import com.example.bamachat.shared.core.github.GitHubResolvedRef
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidGitHubReadOnlyRepositoryGatewayTest {
    @Test
    fun metadataRequestUsesGetAllowedHostAndNoCredentials() = runBlocking {
        val executor = RecordingExecutor {
            GitHubHttpResult.Success(
                200,
                """{"default_branch":"main","description":"Public repository"}"""
            )
        }
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        val result = gateway.readRepositoryMetadata(GitHubRepositoryPolicy.repository)

        assertTrue(result is GitHubReadResult.Success)
        val request = executor.requests.single()
        assertEquals("GET", request.method)
        assertEquals("api.github.com", request.url.host)
        assertEquals("application/vnd.github+json", request.header("Accept"))
        assertTrue(requireNotNull(request.header("User-Agent")).startsWith("BamaChat-"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
    }

    @Test
    fun foreignRepositoryIsRejectedBeforeNetwork() = runBlocking {
        val executor = RecordingExecutor { error("Network must not be used") }
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        val result = gateway.readRepositoryMetadata(
            GitHubRepositoryPolicy.repository.copy(owner = "foreign-owner")
        )

        assertEquals(
            GitHubReadResult.Failure(GitHubReadIssue.REPOSITORY_NOT_ALLOWED),
            result
        )
        assertTrue(executor.requests.isEmpty())
    }

    @Test
    fun foreignHostIsRejectedByTransport() = runBlocking {
        val executor = OkHttpGitHubReadOnlyHttpExecutor()
        val result = executor.get(Request.Builder().url("https://example.invalid/repo").get().build())

        assertEquals(
            GitHubHttpResult.Failure(GitHubReadIssue.REPOSITORY_NOT_ALLOWED),
            result
        )
    }

    @Test
    fun redirectsRateLimitsAccessDeniedAndNotFoundAreMappedSafely() = runBlocking {
        suspend fun issueFor(response: GitHubHttpResult.Success): GitHubReadResult.Failure {
            val gateway = AndroidGitHubReadOnlyRepositoryGateway(
                RecordingExecutor { response }
            )
            return gateway.readRepositoryMetadata(
                GitHubRepositoryPolicy.repository
            ) as GitHubReadResult.Failure
        }

        assertEquals(GitHubReadIssue.REDIRECT_BLOCKED, issueFor(GitHubHttpResult.Success(302, "{}")).issue)
        assertEquals(GitHubReadIssue.ACCESS_DENIED, issueFor(GitHubHttpResult.Success(403, "{}")).issue)
        assertEquals(
            GitHubReadIssue.RATE_LIMITED,
            issueFor(GitHubHttpResult.Success(403, "{}", rateLimitRemaining = 0)).issue
        )
        assertEquals(
            GitHubReadIssue.RATE_LIMITED,
            issueFor(
                GitHubHttpResult.Success(403, """{"message":"API rate limit exceeded"}""")
            ).issue
        )
        assertEquals(GitHubReadIssue.RATE_LIMITED, issueFor(GitHubHttpResult.Success(429, "{}")).issue)
        assertEquals(GitHubReadIssue.NOT_FOUND, issueFor(GitHubHttpResult.Success(404, "{}")).issue)
    }

    @Test
    fun rateLimitResetIsPropagatedWithoutRawResponse() = runBlocking {
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(
            RecordingExecutor {
                GitHubHttpResult.Success(
                    code = 403,
                    body = """{"message":"technical body must stay internal"}""",
                    rateLimitRemaining = 0,
                    rateLimitResetEpochSeconds = 1_785_532_680L
                )
            }
        )

        val failure = gateway.readRepositoryMetadata(
            GitHubRepositoryPolicy.repository
        ) as GitHubReadResult.Failure

        assertEquals(GitHubReadIssue.RATE_LIMITED, failure.issue)
        assertEquals(1_785_532_680L, failure.rateLimitResetEpochSeconds)
        assertFalse(failure.toString().contains("technical body"))
    }

    @Test
    fun transportReadsOnlySafeRateLimitHeadersAndIgnoresInvalidReset() = runBlocking {
        val request = Request.Builder()
            .url("https://api.github.com/repos/blackstarr595384-stack/BamaChat")
            .get()
            .build()
        val validResponse = response(
            request = request,
            code = 403,
            body = "{}",
            headers = mapOf(
                "X-RateLimit-Limit" to "60",
                "X-RateLimit-Remaining" to "0",
                "X-RateLimit-Reset" to "1785532680",
                "Authorization" to "must-not-be-copied"
            )
        )
        val validExecutor = OkHttpGitHubReadOnlyHttpExecutor(
            client = OkHttpGitHubReadOnlyHttpExecutor.createClient(),
            callFactory = Call.Factory { ImmediateResponseCall(request, validResponse) }
        )

        val valid = validExecutor.get(request) as GitHubHttpResult.Success

        assertEquals(60, valid.rateLimitLimit)
        assertEquals(0, valid.rateLimitRemaining)
        assertEquals(1_785_532_680L, valid.rateLimitResetEpochSeconds)
        assertFalse(valid.toString().contains("must-not-be-copied"))

        val invalidResponse = response(
            request = request,
            code = 429,
            body = "{}",
            headers = mapOf("X-RateLimit-Reset" to "invalid")
        )
        val invalidExecutor = OkHttpGitHubReadOnlyHttpExecutor(
            client = OkHttpGitHubReadOnlyHttpExecutor.createClient(),
            callFactory = Call.Factory { ImmediateResponseCall(request, invalidResponse) }
        )

        val invalid = invalidExecutor.get(request) as GitHubHttpResult.Success

        assertNull(invalid.rateLimitResetEpochSeconds)
    }

    @Test
    fun dedicatedClientDisablesRedirectsAndRetries() {
        val client = OkHttpGitHubReadOnlyHttpExecutor.createClient()

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
    }

    @Test
    fun contractContainsNoWriteOperations() {
        val methodNames = GitHubReadOnlyRepositoryGateway::class.java.methods
            .map { it.name.lowercase() }
        val forbidden = listOf(
            "create",
            "update",
            "delete",
            "push",
            "merge",
            "pullrequest",
            "issuecreate",
            "comment",
            "workflowwrite",
            "secret",
            "tokenwrite"
        )

        forbidden.forEach { term ->
            assertFalse(term, methodNames.any { it.contains(term) })
        }
    }

    @Test
    fun treeIsLimitedFilteredAndDeterministicallySorted() = runBlocking {
        val tree = buildString {
            append("""{"truncated":false,"tree":[""")
            repeat(2_100) { index ->
                if (index > 0) append(',')
                append(
                    """{"path":"src/File${2_100 - index}.kt","type":"blob","mode":"100644","size":12,"sha":"$SHA"}"""
                )
            }
            append(
                """,{"path":"local.properties","type":"blob","mode":"100644","size":12,"sha":"$SHA"}]}"""
            )
        }
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(
            RecordingExecutor { GitHubHttpResult.Success(200, tree) }
        )

        val result = gateway.readTree(GitHubRepositoryPolicy.repository, resolvedRef())

        assertTrue(result is GitHubReadResult.Success)
        val entries = (result as GitHubReadResult.Success).value
        assertEquals(GitHubReadLimits.MAX_TREE_ENTRIES, entries.size)
        assertEquals(entries.sortedBy { it.path }, entries)
        assertFalse(entries.any { it.path == "local.properties" })
    }

    @Test
    fun treeAcceptsOnlyRegularFileModes() = runBlocking {
        val tree = """
            {
              "truncated":false,
              "tree":[
                {"path":"src/Regular.kt","type":"blob","mode":"100644","size":12,"sha":"$SHA"},
                {"path":"scripts/Executable.sh.txt","type":"blob","mode":"100755","size":12,"sha":"$SHA"},
                {"path":"docs/Symlink.md","type":"blob","mode":"120000","size":12,"sha":"$SHA"},
                {"path":"vendor/Submodule.md","type":"commit","mode":"160000","size":12,"sha":"$SHA"},
                {"path":"docs/Unknown.md","type":"blob","mode":"100600","size":12,"sha":"$SHA"},
                {"path":"docs/Missing.md","type":"blob","size":12,"sha":"$SHA"}
              ]
            }
        """.trimIndent()
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(
            RecordingExecutor { GitHubHttpResult.Success(200, tree) }
        )

        val result = gateway.readTree(GitHubRepositoryPolicy.repository, resolvedRef())

        assertTrue(result is GitHubReadResult.Success)
        val entries = (result as GitHubReadResult.Success).value
        assertEquals(
            listOf("scripts/Executable.sh.txt", "src/Regular.kt"),
            entries.map { it.path }
        )
    }

    @Test
    fun symlinkNeverTriggersBlobReadOrEntersSnapshot() = runBlocking {
        val executor = RecordingExecutor { request ->
            when {
                request.url.encodedPath.endsWith("/commits/phase-7.5b-stable") ->
                    GitHubHttpResult.Success(200, """{"sha":"$SHA"}""")
                request.url.encodedPath.endsWith("/git/trees/$SHA") ->
                    GitHubHttpResult.Success(
                        200,
                        """
                            {
                              "truncated":false,
                              "tree":[
                                {
                                  "path":"docs/SafeName.md",
                                  "type":"blob",
                                  "mode":"120000",
                                  "size":16,
                                  "sha":"$SYMLINK_SHA"
                                }
                              ]
                            }
                        """.trimIndent()
                    )
                "/git/blobs/" in request.url.encodedPath ->
                    error("A rejected symlink must never be dereferenced")
                else -> GitHubHttpResult.Success(
                    200,
                    """{"default_branch":"main","description":"Public repository"}"""
                )
            }
        }
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        val direct = gateway.readTextFile(
            GitHubRepositoryPolicy.repository,
            resolvedRef(),
            "docs/SafeName.md"
        )
        val snapshot = gateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.DOCUMENTATION
        )

        assertEquals(
            GitHubReadResult.Failure(GitHubReadIssue.PATH_NOT_ALLOWED),
            direct
        )
        assertTrue(snapshot is GitHubReadResult.Success)
        val value = (snapshot as GitHubReadResult.Success).value
        assertTrue(value.selectedFiles.isEmpty())
        assertTrue(
            value.truncationInformation.reasons.contains(
                com.example.bamachat.shared.core.github.GitHubTruncationReason.UNSAFE_PATH_SKIPPED
            )
        )
        assertTrue(executor.requests.none { "/git/blobs/" in it.url.encodedPath })
        assertTrue(executor.requests.none { "/contents/" in it.url.encodedPath })
    }

    @Test
    fun blobShaMustMatchValidatedTreeEntry() = runBlocking {
        val executor = RecordingExecutor { request ->
            when {
                request.url.encodedPath.endsWith("/git/trees/$SHA") ->
                    GitHubHttpResult.Success(200, singleFileTree())
                request.url.encodedPath.endsWith("/git/blobs/$SHA") ->
                    GitHubHttpResult.Success(200, blobJson("content", SHA_MISMATCH))
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        val result = gateway.readTextFile(
            GitHubRepositoryPolicy.repository,
            resolvedRef(),
            "README.md"
        )

        assertEquals(
            GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE),
            result
        )
    }

    @Test
    fun oversizedAndInvalidEncodingFilesAreRejected() = runBlocking {
        val oversizedGateway = AndroidGitHubReadOnlyRepositoryGateway(
            RecordingExecutor { request ->
                if (request.url.encodedPath.endsWith("/git/trees/$SHA")) {
                    GitHubHttpResult.Success(200, singleFileTree())
                } else {
                    GitHubHttpResult.Success(
                        200,
                        """{"sha":"$SHA","encoding":"base64","size":256001,"content":""}"""
                    )
                }
            }
        )
        val invalidEncodingGateway = AndroidGitHubReadOnlyRepositoryGateway(
            RecordingExecutor { request ->
                if (request.url.encodedPath.endsWith("/git/trees/$SHA")) {
                    GitHubHttpResult.Success(200, singleFileTree())
                } else {
                    GitHubHttpResult.Success(
                        200,
                        """{"sha":"$SHA","encoding":"none","size":4,"content":"text"}"""
                    )
                }
            }
        )

        assertEquals(
            GitHubReadResult.Failure(GitHubReadIssue.RESPONSE_TOO_LARGE),
            oversizedGateway.readTextFile(
                GitHubRepositoryPolicy.repository,
                resolvedRef(),
                "README.md"
            )
        )
        assertEquals(
            GitHubReadResult.Failure(GitHubReadIssue.UNSUPPORTED_ENCODING),
            invalidEncodingGateway.readTextFile(
                GitHubRepositoryPolicy.repository,
                resolvedRef(),
                "README.md"
            )
        )
    }

    @Test
    fun snapshotUsesLimitsAndReadsOnlySelectedSafeFiles() = runBlocking {
        val executor = RecordingExecutor { request ->
            when {
                request.url.encodedPath.endsWith("/commits/phase-7.5b-stable") ->
                    GitHubHttpResult.Success(200, """{"sha":"$SHA"}""")
                request.url.encodedPath.endsWith("/git/trees/$SHA") ->
                    GitHubHttpResult.Success(200, documentationTree(40))
                "/git/blobs/" in request.url.encodedPath ->
                    GitHubHttpResult.Success(
                        200,
                        blobJson("snapshot content", request.url.pathSegments.last())
                    )
                else -> GitHubHttpResult.Success(
                    200,
                    """{"default_branch":"main","description":"Public repository"}"""
                )
            }
        }
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        val result = gateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.DOCUMENTATION
        )

        assertTrue(result is GitHubReadResult.Success)
        val snapshot = (result as GitHubReadResult.Success).value
        assertEquals(GitHubReadLimits.MAX_FILES, snapshot.selectedFiles.size)
        assertTrue(snapshot.selectedFiles.all { it.path.endsWith(".md") })
        assertEquals(
            GitHubReadLimits.MAX_FILES,
            executor.requests.count { "/git/blobs/" in it.url.encodedPath }
        )
        assertEquals(12, snapshot.selectedFiles.size)
        assertEquals(
            GitHubReadLimits.MAX_COLD_NETWORK_REQUESTS_PER_SNAPSHOT,
            executor.requests.size
        )
        assertTrue(executor.requests.all { it.method == "GET" })
        assertTrue(executor.requests.none { it.header("Authorization") != null })
        assertTrue(executor.requests.none { "/contents/" in it.url.encodedPath })
    }

    @Test
    fun identicalSecondSnapshotUsesNoNewNetworkRequestsAndRemainsDeterministic() = runBlocking {
        val executor = snapshotExecutor(documentationTree(40))
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        val first = gateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.DOCUMENTATION
        )
        val requestsAfterFirst = executor.requests.size
        val second = gateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.DOCUMENTATION
        )

        assertEquals(GitHubReadLimits.MAX_COLD_NETWORK_REQUESTS_PER_SNAPSHOT, requestsAfterFirst)
        assertEquals(requestsAfterFirst, executor.requests.size)
        assertEquals(first, second)
    }

    @Test
    fun anotherAnalysisAreaReusesMetadataRefAndTreeForSameCommit() = runBlocking {
        val executor = snapshotExecutor(mixedAnalysisTree())
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        gateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.DOCUMENTATION
        )
        gateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.SECURITY
        )

        assertEquals(1, executor.requests.count { it.url.encodedPath.endsWith("/BamaChat") })
        assertEquals(1, executor.requests.count { "/commits/" in it.url.encodedPath })
        assertEquals(1, executor.requests.count { "/git/trees/" in it.url.encodedPath })
    }

    @Test
    fun requestBudgetStopsBeforeAnotherNetworkCallAndReturnsTypedTruncation() = runBlocking {
        val executor = snapshotExecutor(documentationTree(40))
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(
            httpExecutor = executor,
            maxColdNetworkRequestsPerSnapshot = 4
        )

        val result = gateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.DOCUMENTATION
        )

        assertTrue(result is GitHubReadResult.Success)
        val snapshot = (result as GitHubReadResult.Success).value
        assertEquals(4, executor.requests.size)
        assertEquals(1, snapshot.selectedFiles.size)
        assertTrue(
            snapshot.truncationInformation.reasons.contains(
                com.example.bamachat.shared.core.github.GitHubTruncationReason.NETWORK_REQUEST_LIMIT
            )
        )
        assertEquals(GitHubReadLimits.MAX_FILES - 1, snapshot.truncationInformation.omittedFiles)
    }

    @Test
    fun blobCacheEnforcesEntryAndByteLimitsWithDeterministicLruEviction() = runBlocking {
        val entryExecutor = fileExecutor(threeFileTree(), text = "123456")
        val entryGateway = AndroidGitHubReadOnlyRepositoryGateway(
            httpExecutor = entryExecutor,
            cacheLimits = GitHubMemoryCacheLimits(maxBlobEntries = 2)
        )
        entryGateway.readTextFile(GitHubRepositoryPolicy.repository, resolvedRef(), "docs/A.md")
        entryGateway.readTextFile(GitHubRepositoryPolicy.repository, resolvedRef(), "docs/B.md")
        entryGateway.readTextFile(GitHubRepositoryPolicy.repository, resolvedRef(), "docs/A.md")
        entryGateway.readTextFile(GitHubRepositoryPolicy.repository, resolvedRef(), "docs/C.md")
        entryGateway.readTextFile(GitHubRepositoryPolicy.repository, resolvedRef(), "docs/B.md")

        assertEquals(
            2,
            entryExecutor.requests.count { it.url.encodedPath.endsWith("/git/blobs/${shaFor(1)}") }
        )

        val byteExecutor = fileExecutor(threeFileTree(), text = "123456")
        val byteGateway = AndroidGitHubReadOnlyRepositoryGateway(
            httpExecutor = byteExecutor,
            cacheLimits = GitHubMemoryCacheLimits(maxBlobTextBytes = 10)
        )
        byteGateway.readTextFile(GitHubRepositoryPolicy.repository, resolvedRef(), "docs/A.md")
        byteGateway.readTextFile(GitHubRepositoryPolicy.repository, resolvedRef(), "docs/B.md")
        byteGateway.readTextFile(GitHubRepositoryPolicy.repository, resolvedRef(), "docs/A.md")

        assertEquals(
            2,
            byteExecutor.requests.count { it.url.encodedPath.endsWith("/git/blobs/${shaFor(0)}") }
        )
    }

    @Test
    fun expiredCacheEntriesAreReloadedAndFailuresAreNeverCached() = runBlocking {
        var now = 1_000L
        val expiringExecutor = snapshotExecutor(documentationTree(2))
        val expiringGateway = AndroidGitHubReadOnlyRepositoryGateway(
            httpExecutor = expiringExecutor,
            currentTimeMillis = { now }
        )
        expiringGateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.DOCUMENTATION
        )
        val firstRequestCount = expiringExecutor.requests.size
        now += MAX_GITHUB_CACHE_TTL_MILLIS + 1L
        expiringGateway.readSnapshot(
            GitHubRepositoryPolicy.repository,
            GitHubRepositoryPolicy.DEFAULT_REF,
            GitHubAnalysisArea.DOCUMENTATION
        )

        assertEquals(firstRequestCount * 2, expiringExecutor.requests.size)

        var metadataCalls = 0
        val failureExecutor = RecordingExecutor {
            metadataCalls++
            if (metadataCalls == 1) {
                GitHubHttpResult.Success(503, "{}")
            } else {
                GitHubHttpResult.Success(200, repositoryMetadataJson())
            }
        }
        val failureGateway = AndroidGitHubReadOnlyRepositoryGateway(failureExecutor)

        assertTrue(
            failureGateway.readRepositoryMetadata(GitHubRepositoryPolicy.repository) is GitHubReadResult.Failure
        )
        assertTrue(
            failureGateway.readRepositoryMetadata(GitHubRepositoryPolicy.repository) is GitHubReadResult.Success
        )
        assertEquals(2, failureExecutor.requests.size)
    }

    @Test
    fun invalidShaResponseNeverEntersBlobCache() = runBlocking {
        var blobCalls = 0
        val executor = RecordingExecutor { request ->
            when {
                request.url.encodedPath.endsWith("/git/trees/$SHA") ->
                    GitHubHttpResult.Success(200, singleFileTree())
                request.url.encodedPath.endsWith("/git/blobs/$SHA") -> {
                    blobCalls++
                    val responseSha = if (blobCalls == 1) SHA_MISMATCH else SHA
                    GitHubHttpResult.Success(200, blobJson("content", responseSha))
                }
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val gateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        val first = gateway.readTextFile(
            GitHubRepositoryPolicy.repository,
            resolvedRef(),
            "README.md"
        )
        val second = gateway.readTextFile(
            GitHubRepositoryPolicy.repository,
            resolvedRef(),
            "README.md"
        )

        assertEquals(GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE), first)
        assertTrue(second is GitHubReadResult.Success)
        assertEquals(2, blobCalls)
    }

    @Test
    fun cacheIsInstanceLocalAndNeverSharedPersistently() = runBlocking {
        val executor = RecordingExecutor { GitHubHttpResult.Success(200, repositoryMetadataJson()) }
        val firstGateway = AndroidGitHubReadOnlyRepositoryGateway(executor)
        val secondGateway = AndroidGitHubReadOnlyRepositoryGateway(executor)

        firstGateway.readRepositoryMetadata(GitHubRepositoryPolicy.repository)
        firstGateway.readRepositoryMetadata(GitHubRepositoryPolicy.repository)
        secondGateway.readRepositoryMetadata(GitHubRepositoryPolicy.repository)

        assertEquals(2, executor.requests.size)
    }

    @Test
    fun cancellationCancelsTheUnderlyingHttpCall() = runBlocking {
        val blockingCall = BlockingCall(
            Request.Builder()
                .url("https://api.github.com/repos/blackstarr595384-stack/BamaChat")
                .get()
                .build()
        )
        val callFactory = Call.Factory { blockingCall }
        val executor = OkHttpGitHubReadOnlyHttpExecutor(
            client = OkHttpGitHubReadOnlyHttpExecutor.createClient(),
            callFactory = callFactory
        )

        val request = Request.Builder()
            .url("https://api.github.com/repos/blackstarr595384-stack/BamaChat")
            .get()
            .build()
        val deferred = async(Dispatchers.Default) { executor.get(request) }
        assertTrue(blockingCall.started.await(2, TimeUnit.SECONDS))
        deferred.cancel()
        var cancellationObserved = false
        try {
            deferred.await()
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
        assertTrue(blockingCall.isCanceled())
    }

    private fun resolvedRef(): GitHubResolvedRef {
        return GitHubResolvedRef(GitHubRepositoryPolicy.DEFAULT_REF, SHA)
    }

    private fun documentationTree(fileCount: Int): String {
        return buildString {
            append("""{"truncated":false,"tree":[""")
            repeat(fileCount) { index ->
                if (index > 0) append(',')
                append(
                    """{"path":"docs/File$index.md","type":"blob","mode":"100644","size":24,"sha":"${shaFor(index)}"}"""
                )
            }
            append("]}")
        }
    }

    private fun mixedAnalysisTree(): String {
        return """
            {
              "truncated":false,
              "tree":[
                {"path":"docs/Overview.md","type":"blob","mode":"100644","size":24,"sha":"${shaFor(0)}"},
                {"path":"app/src/main/java/com/example/bamachat/security/Policy.kt","type":"blob","mode":"100644","size":24,"sha":"${shaFor(1)}"}
              ]
            }
        """.trimIndent()
    }

    private fun threeFileTree(): String {
        return """
            {
              "truncated":false,
              "tree":[
                {"path":"docs/A.md","type":"blob","mode":"100644","size":6,"sha":"${shaFor(0)}"},
                {"path":"docs/B.md","type":"blob","mode":"100644","size":6,"sha":"${shaFor(1)}"},
                {"path":"docs/C.md","type":"blob","mode":"100644","size":6,"sha":"${shaFor(2)}"}
              ]
            }
        """.trimIndent()
    }

    private fun singleFileTree(): String {
        return """
            {
              "truncated":false,
              "tree":[
                {
                  "path":"README.md",
                  "type":"blob",
                  "mode":"100644",
                  "size":7,
                  "sha":"$SHA"
                }
              ]
            }
        """.trimIndent()
    }

    private fun blobJson(
        text: String,
        sha: String = SHA
    ): String {
        val encoded = Base64.getEncoder().encodeToString(text.toByteArray())
        return """{"sha":"$sha","encoding":"base64","size":${text.length},"content":"$encoded"}"""
    }

    private fun repositoryMetadataJson(): String {
        return """{"default_branch":"main","description":"Public repository"}"""
    }

    private fun snapshotExecutor(tree: String): RecordingExecutor {
        return RecordingExecutor { request ->
            when {
                request.url.encodedPath.endsWith("/commits/phase-7.5b-stable") ->
                    GitHubHttpResult.Success(200, """{"sha":"$SHA"}""")
                request.url.encodedPath.endsWith("/git/trees/$SHA") ->
                    GitHubHttpResult.Success(200, tree)
                "/git/blobs/" in request.url.encodedPath ->
                    GitHubHttpResult.Success(
                        200,
                        blobJson("snapshot content", request.url.pathSegments.last())
                    )
                else -> GitHubHttpResult.Success(200, repositoryMetadataJson())
            }
        }
    }

    private fun fileExecutor(tree: String, text: String): RecordingExecutor {
        return RecordingExecutor { request ->
            when {
                request.url.encodedPath.endsWith("/git/trees/$SHA") ->
                    GitHubHttpResult.Success(200, tree)
                "/git/blobs/" in request.url.encodedPath ->
                    GitHubHttpResult.Success(200, blobJson(text, request.url.pathSegments.last()))
                else -> error("Unexpected request: ${request.url}")
            }
        }
    }

    private fun response(
        request: Request,
        code: Int,
        body: String,
        headers: Map<String, String>
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Test response")
            .body(body.toResponseBody("application/json".toMediaType()))
        headers.forEach(builder::header)
        return builder.build()
    }

    private fun shaFor(index: Int): String = index.toString(16).padStart(40, '0')

    private class RecordingExecutor(
        private val responder: (Request) -> GitHubHttpResult
    ) : GitHubReadOnlyHttpExecutor {
        val requests = mutableListOf<Request>()

        override suspend fun get(request: Request): GitHubHttpResult {
            requests += request
            return responder(request)
        }
    }

    private class BlockingCall(
        private val originalRequest: Request
    ) : Call {
        val started = CountDownLatch(1)
        private val cancelled = AtomicBoolean(false)
        private val executed = AtomicBoolean(false)
        private val callback = AtomicReference<Callback?>()

        override fun request(): Request = originalRequest

        override fun execute(): Response {
            throw UnsupportedOperationException()
        }

        override fun enqueue(responseCallback: Callback) {
            executed.set(true)
            callback.set(responseCallback)
            started.countDown()
        }

        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) {
                callback.get()?.onFailure(this, IOException("cancelled"))
            }
        }

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = cancelled.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = BlockingCall(originalRequest)
    }

    private class ImmediateResponseCall(
        private val originalRequest: Request,
        private val response: Response
    ) : Call {
        private val cancelled = AtomicBoolean(false)
        private val executed = AtomicBoolean(false)

        override fun request(): Request = originalRequest

        override fun execute(): Response {
            executed.set(true)
            return response
        }

        override fun enqueue(responseCallback: Callback) {
            executed.set(true)
            if (!cancelled.get()) responseCallback.onResponse(this, response)
        }

        override fun cancel() {
            cancelled.set(true)
        }

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = cancelled.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = ImmediateResponseCall(originalRequest, response)
    }

    companion object {
        private const val SHA = "919b25230ab418817460ec6e0831dc69b6e60d08"
        private const val SHA_MISMATCH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SYMLINK_SHA = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
