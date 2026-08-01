package com.example.bamachat.data.github

import com.example.bamachat.shared.core.github.GitHubAnalysisArea
import com.example.bamachat.shared.core.github.GitHubAnalysisScopeSelector
import com.example.bamachat.shared.core.github.GitHubPathPolicy
import com.example.bamachat.shared.core.github.GitHubReadIssue
import com.example.bamachat.shared.core.github.GitHubReadLimits
import com.example.bamachat.shared.core.github.GitHubReadOnlyRepositoryGateway
import com.example.bamachat.shared.core.github.GitHubReadResult
import com.example.bamachat.shared.core.github.GitHubRepositoryMetadata
import com.example.bamachat.shared.core.github.GitHubRepositoryPolicy
import com.example.bamachat.shared.core.github.GitHubRepositoryRef
import com.example.bamachat.shared.core.github.GitHubRepositorySnapshot
import com.example.bamachat.shared.core.github.GitHubResolvedRef
import com.example.bamachat.shared.core.github.GitHubTextFile
import com.example.bamachat.shared.core.github.GitHubTreeEntry
import com.example.bamachat.shared.core.github.GitHubTreeEntryType
import com.example.bamachat.shared.core.github.GitHubTruncationInformation
import com.example.bamachat.shared.core.github.GitHubTruncationReason
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

class AndroidGitHubReadOnlyRepositoryGateway internal constructor(
    private val httpExecutor: GitHubReadOnlyHttpExecutor,
    currentTimeMillis: () -> Long = { System.currentTimeMillis() },
    private val maxColdNetworkRequestsPerSnapshot: Int =
        GitHubReadLimits.MAX_COLD_NETWORK_REQUESTS_PER_SNAPSHOT,
    cacheLimits: GitHubMemoryCacheLimits = GitHubMemoryCacheLimits()
) : GitHubReadOnlyRepositoryGateway {
    constructor() : this(OkHttpGitHubReadOnlyHttpExecutor())

    private val metadataCache = TimedLruCache<GitHubRepositoryRef, GitHubRepositoryMetadata>(
        maxEntries = MAX_METADATA_CACHE_ENTRIES,
        maxWeight = MAX_METADATA_CACHE_ENTRIES.toLong(),
        ttlMillis = cacheLimits.ttlMillis,
        currentTimeMillis = currentTimeMillis,
        weightOf = { 1L }
    )
    private val resolvedRefCache = TimedLruCache<ResolvedRefCacheKey, GitHubResolvedRef>(
        maxEntries = MAX_RESOLVED_REF_CACHE_ENTRIES,
        maxWeight = MAX_RESOLVED_REF_CACHE_ENTRIES.toLong(),
        ttlMillis = cacheLimits.ttlMillis,
        currentTimeMillis = currentTimeMillis,
        weightOf = { 1L }
    )
    private val treeCache = TimedLruCache<String, TreePayload>(
        maxEntries = MAX_TREE_CACHE_ENTRIES,
        maxWeight = MAX_TREE_CACHE_ENTRIES.toLong(),
        ttlMillis = cacheLimits.ttlMillis,
        currentTimeMillis = currentTimeMillis,
        weightOf = { 1L }
    )
    private val blobCache = TimedLruCache<String, CachedBlob>(
        maxEntries = cacheLimits.maxBlobEntries,
        maxWeight = cacheLimits.maxBlobTextBytes,
        ttlMillis = cacheLimits.ttlMillis,
        currentTimeMillis = currentTimeMillis,
        weightOf = { blob -> blob.text.toByteArray(StandardCharsets.UTF_8).size.toLong() }
    )

    init {
        require(maxColdNetworkRequestsPerSnapshot in 1..GitHubReadLimits.MAX_COLD_NETWORK_REQUESTS_PER_SNAPSHOT)
    }

    override suspend fun readRepositoryMetadata(
        repository: GitHubRepositoryRef
    ): GitHubReadResult<GitHubRepositoryMetadata> = readRepositoryMetadata(repository, budget = null)

    private suspend fun readRepositoryMetadata(
        repository: GitHubRepositoryRef,
        budget: SnapshotNetworkBudget?
    ): GitHubReadResult<GitHubRepositoryMetadata> {
        if (!GitHubRepositoryPolicy.isAllowed(repository)) {
            return GitHubReadResult.Failure(GitHubReadIssue.REPOSITORY_NOT_ALLOWED)
        }
        metadataCache.get(repository)?.let { cached ->
            return GitHubReadResult.Success(cached.copy())
        }
        val result = requestJson(repositoryPath(repository), budget) { json ->
            val defaultBranch = json.string("default_branch")
                ?.takeIf(::isSafeRefValue)
                ?: return@requestJson null
            GitHubRepositoryMetadata(
                repository = repository,
                defaultBranch = defaultBranch,
                description = json.string("description")?.take(MAX_DESCRIPTION_CHARS)
            )
        }
        if (result is GitHubReadResult.Success) {
            metadataCache.put(repository, result.value.copy())
        }
        return result
    }

    override suspend fun resolveRef(
        repository: GitHubRepositoryRef,
        ref: String
    ): GitHubReadResult<GitHubResolvedRef> = resolveRef(repository, ref, budget = null)

    private suspend fun resolveRef(
        repository: GitHubRepositoryRef,
        ref: String,
        budget: SnapshotNetworkBudget?
    ): GitHubReadResult<GitHubResolvedRef> {
        if (!GitHubRepositoryPolicy.isAllowed(repository)) {
            return GitHubReadResult.Failure(GitHubReadIssue.REPOSITORY_NOT_ALLOWED)
        }
        if (!GitHubRepositoryPolicy.isAllowedRef(ref)) {
            return GitHubReadResult.Failure(GitHubReadIssue.REF_NOT_ALLOWED)
        }
        val cacheKey = ResolvedRefCacheKey(repository, ref)
        resolvedRefCache.get(cacheKey)?.let { cached ->
            return GitHubReadResult.Success(cached.copy())
        }
        val result = requestJson(repositoryPath(repository, "commits", ref), budget) { json ->
            val sha = json.string("sha")?.takeIf(::isValidSha) ?: return@requestJson null
            GitHubResolvedRef(resolvedRef = ref, headCommitSha = sha)
        }
        if (result is GitHubReadResult.Success) {
            resolvedRefCache.put(cacheKey, result.value.copy())
        }
        return result
    }

    override suspend fun readTree(
        repository: GitHubRepositoryRef,
        resolvedRef: GitHubResolvedRef
    ): GitHubReadResult<List<GitHubTreeEntry>> {
        return when (val result = readTreePayload(repository, resolvedRef, budget = null)) {
            is GitHubReadResult.Success -> GitHubReadResult.Success(
                result.value.entries.map { it.copy() }
            )
            is GitHubReadResult.Failure -> result
        }
    }

    override suspend fun readTextFile(
        repository: GitHubRepositoryRef,
        resolvedRef: GitHubResolvedRef,
        path: String
    ): GitHubReadResult<GitHubTextFile> {
        val validationFailure = validateRead(repository, resolvedRef, path)
        if (validationFailure != null) return validationFailure

        val treePayload = when (val result = readTreePayload(repository, resolvedRef, budget = null)) {
            is GitHubReadResult.Success -> result.value
            is GitHubReadResult.Failure -> return result
        }
        val expectedEntry = treePayload.entries.singleOrNull { it.path == path }
            ?: return GitHubReadResult.Failure(GitHubReadIssue.PATH_NOT_ALLOWED)
        return readValidatedTextFile(repository, resolvedRef, expectedEntry, budget = null)
    }

    private suspend fun readValidatedTextFile(
        repository: GitHubRepositoryRef,
        resolvedRef: GitHubResolvedRef,
        expectedEntry: GitHubTreeEntry,
        budget: SnapshotNetworkBudget?
    ): GitHubReadResult<GitHubTextFile> {
        val validationFailure = validateRead(repository, resolvedRef, expectedEntry.path)
        if (validationFailure != null) return validationFailure
        if (!isValidSha(expectedEntry.sha)) {
            return GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE)
        }

        blobCache.get(expectedEntry.sha)?.let { cached ->
            if (cached.sha != expectedEntry.sha) {
                return GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE)
            }
            return GitHubReadResult.Success(cached.toTextFile(expectedEntry.path))
        }

        val url = repositoryUrl(repository, "git", "blobs", expectedEntry.sha)
        val result = requestJson(url, budget) { json ->
            val sha = json.string("sha")?.takeIf(::isValidSha) ?: return@requestJson null
            if (sha != expectedEntry.sha) return@requestJson null
            val encoding = json.string("encoding")?.lowercase()
            if (encoding != BASE64_ENCODING) throw UnsupportedTextEncoding()
            val declaredSize = json.long("size") ?: return@requestJson null
            if (declaredSize < 0L || declaredSize > GitHubReadLimits.MAX_ORIGINAL_FILE_BYTES) {
                throw FileLimitExceeded()
            }
            val encoded = json.string("content") ?: return@requestJson null
            val bytes = runCatching { Base64.getMimeDecoder().decode(encoded) }.getOrNull()
                ?: return@requestJson null
            if (bytes.size > GitHubReadLimits.MAX_ORIGINAL_FILE_BYTES) throw FileLimitExceeded()
            val decoded = decodeUtf8(bytes) ?: throw UnsupportedTextEncoding()
            val originalSize = maxOf(declaredSize, bytes.size.toLong())
            val bounded = truncateUtf8(decoded, GitHubReadLimits.MAX_FILE_TEXT_BYTES)
            CachedBlob(
                sha = sha,
                text = bounded.text,
                truncated = bounded.truncated,
                originalSize = originalSize
            )
        }
        return when (result) {
            is GitHubReadResult.Success -> {
                blobCache.put(expectedEntry.sha, result.value)
                GitHubReadResult.Success(result.value.toTextFile(expectedEntry.path))
            }
            is GitHubReadResult.Failure -> result
        }
    }

    override suspend fun readSnapshot(
        repository: GitHubRepositoryRef,
        ref: String,
        analysisArea: GitHubAnalysisArea
    ): GitHubReadResult<GitHubRepositorySnapshot> {
        val budget = SnapshotNetworkBudget(maxColdNetworkRequestsPerSnapshot)
        val metadata = when (val result = readRepositoryMetadata(repository, budget)) {
            is GitHubReadResult.Success -> result.value
            is GitHubReadResult.Failure -> return result
        }
        val resolved = when (val result = resolveRef(repository, ref, budget)) {
            is GitHubReadResult.Success -> result.value
            is GitHubReadResult.Failure -> return result
        }
        val treePayload = when (val result = readTreePayload(repository, resolved, budget)) {
            is GitHubReadResult.Success -> result.value
            is GitHubReadResult.Failure -> return result
        }
        val selectedPaths = GitHubAnalysisScopeSelector.select(treePayload.entries, analysisArea)
        val selectedFiles = mutableListOf<GitHubTextFile>()
        val reasons = treePayload.reasons.toMutableSet()
        var omittedFiles = treePayload.omittedUnsafeEntries
        var omittedTextBytes = 0L
        var usedTextBytes = 0

        for ((pathIndex, path) in selectedPaths.withIndex()) {
            if (selectedFiles.size >= GitHubReadLimits.MAX_FILES) {
                reasons += GitHubTruncationReason.FILE_COUNT_LIMIT
                omittedFiles += selectedPaths.size - pathIndex
                break
            }
            val expectedEntry = treePayload.entries.singleOrNull { it.path == path }
            if (expectedEntry == null) {
                reasons += GitHubTruncationReason.UNSAFE_PATH_SKIPPED
                omittedFiles++
                continue
            }
            val file = when (
                val result = readValidatedTextFile(repository, resolved, expectedEntry, budget)
            ) {
                is GitHubReadResult.Success -> result.value
                is GitHubReadResult.Failure -> when (result.issue) {
                    GitHubReadIssue.NETWORK_REQUEST_BUDGET_EXHAUSTED -> {
                        reasons += GitHubTruncationReason.NETWORK_REQUEST_LIMIT
                        omittedFiles += selectedPaths.size - pathIndex
                        break
                    }
                    GitHubReadIssue.RESPONSE_TOO_LARGE -> {
                        reasons += GitHubTruncationReason.FILE_SIZE_LIMIT
                        omittedFiles++
                        continue
                    }
                    GitHubReadIssue.PATH_NOT_ALLOWED -> {
                        reasons += GitHubTruncationReason.UNSAFE_PATH_SKIPPED
                        omittedFiles++
                        continue
                    }
                    else -> return result
                }
            }
            val remainingBytes = GitHubReadLimits.MAX_SNAPSHOT_TEXT_BYTES - usedTextBytes
            if (remainingBytes <= 0) {
                reasons += GitHubTruncationReason.TOTAL_TEXT_LIMIT
                omittedFiles++
                omittedTextBytes += file.text.toByteArray(StandardCharsets.UTF_8).size
                continue
            }
            val bounded = truncateUtf8(file.text, remainingBytes)
            val stored = file.copy(
                text = bounded.text,
                truncated = file.truncated || bounded.truncated
            )
            if (stored.truncated) reasons += GitHubTruncationReason.FILE_TEXT_LIMIT
            if (bounded.truncated) {
                reasons += GitHubTruncationReason.TOTAL_TEXT_LIMIT
                omittedTextBytes += (
                    file.text.toByteArray(StandardCharsets.UTF_8).size -
                        bounded.text.toByteArray(StandardCharsets.UTF_8).size
                    )
            }
            selectedFiles += stored
            usedTextBytes += stored.text.toByteArray(StandardCharsets.UTF_8).size
        }

        return GitHubReadResult.Success(
            GitHubRepositorySnapshot(
                repository = repository,
                resolvedRef = resolved.resolvedRef,
                headCommitSha = resolved.headCommitSha,
                defaultBranch = metadata.defaultBranch,
                repositoryDescription = metadata.description,
                treeEntries = treePayload.entries.map { it.copy() },
                selectedFiles = selectedFiles.sortedBy { it.path },
                truncationInformation = GitHubTruncationInformation(
                    reasons = reasons,
                    omittedTreeEntries = treePayload.omittedTreeEntries,
                    omittedFiles = omittedFiles,
                    omittedTextBytes = omittedTextBytes
                )
            )
        )
    }

    private suspend fun readTreePayload(
        repository: GitHubRepositoryRef,
        resolvedRef: GitHubResolvedRef,
        budget: SnapshotNetworkBudget?
    ): GitHubReadResult<TreePayload> {
        if (!GitHubRepositoryPolicy.isAllowed(repository)) {
            return GitHubReadResult.Failure(GitHubReadIssue.REPOSITORY_NOT_ALLOWED)
        }
        if (!GitHubRepositoryPolicy.isAllowedRef(resolvedRef.resolvedRef)) {
            return GitHubReadResult.Failure(GitHubReadIssue.REF_NOT_ALLOWED)
        }
        if (!isValidSha(resolvedRef.headCommitSha)) {
            return GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE)
        }
        treeCache.get(resolvedRef.headCommitSha)?.let { cached ->
            return GitHubReadResult.Success(cached.defensiveCopy())
        }
        val url = repositoryUrl(repository, "git", "trees", resolvedRef.headCommitSha)
            .newBuilder()
            .addQueryParameter("recursive", "1")
            .build()
        val result = requestJson(url, budget) { json ->
            val tree = json.getAsJsonArray("tree") ?: return@requestJson null
            val safeEntries = mutableListOf<GitHubTreeEntry>()
            var unsafeEntries = 0
            tree.forEach { element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val gitType = item.string("type")
                if (gitType == GIT_TYPE_TREE) return@forEach
                val mode = item.string("mode")
                if (
                    gitType != GIT_TYPE_BLOB ||
                    mode == null ||
                    mode !in REGULAR_FILE_MODES
                ) {
                    unsafeEntries++
                    return@forEach
                }
                val path = item.string("path") ?: run {
                    unsafeEntries++
                    return@forEach
                }
                if (!GitHubPathPolicy.isAllowed(path)) {
                    unsafeEntries++
                    return@forEach
                }
                val sha = item.string("sha")?.takeIf(::isValidSha) ?: run {
                    unsafeEntries++
                    return@forEach
                }
                if (safeEntries.size >= GitHubReadLimits.MAX_TREE_ENTRIES) return@forEach
                val size = item.long("size")
                safeEntries += GitHubTreeEntry(path, GitHubTreeEntryType.FILE, size, sha)
            }
            val reasons = mutableSetOf<GitHubTruncationReason>()
            val remoteTruncated = json.boolean("truncated") == true
            if (remoteTruncated) reasons += GitHubTruncationReason.REMOTE_TREE_TRUNCATED
            val omittedByLimit = (tree.size() - GitHubReadLimits.MAX_TREE_ENTRIES).coerceAtLeast(0)
            if (omittedByLimit > 0) reasons += GitHubTruncationReason.TREE_ENTRY_LIMIT
            if (unsafeEntries > 0) reasons += GitHubTruncationReason.UNSAFE_PATH_SKIPPED
            TreePayload(
                entries = safeEntries.sortedBy { it.path },
                reasons = reasons,
                omittedTreeEntries = omittedByLimit,
                omittedUnsafeEntries = unsafeEntries
            )
        }
        if (result is GitHubReadResult.Success) {
            treeCache.put(resolvedRef.headCommitSha, result.value.defensiveCopy())
            return GitHubReadResult.Success(result.value.defensiveCopy())
        }
        return result
    }

    private fun validateRead(
        repository: GitHubRepositoryRef,
        resolvedRef: GitHubResolvedRef,
        path: String
    ): GitHubReadResult.Failure? {
        if (!GitHubRepositoryPolicy.isAllowed(repository)) {
            return GitHubReadResult.Failure(GitHubReadIssue.REPOSITORY_NOT_ALLOWED)
        }
        if (!GitHubRepositoryPolicy.isAllowedRef(resolvedRef.resolvedRef)) {
            return GitHubReadResult.Failure(GitHubReadIssue.REF_NOT_ALLOWED)
        }
        if (!isValidSha(resolvedRef.headCommitSha)) {
            return GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE)
        }
        if (!GitHubPathPolicy.isAllowed(path)) {
            return GitHubReadResult.Failure(GitHubReadIssue.PATH_NOT_ALLOWED)
        }
        return null
    }

    private suspend fun <T> requestJson(
        url: HttpUrl,
        budget: SnapshotNetworkBudget? = null,
        mapper: (JsonObject) -> T?
    ): GitHubReadResult<T> {
        val request = Request.Builder()
            .url(url)
            .get()
            .header(HEADER_ACCEPT, GITHUB_JSON_ACCEPT)
            .header(HEADER_USER_AGENT, USER_AGENT)
            .build()
        if (budget != null && !budget.tryAcquire()) {
            return GitHubReadResult.Failure(GitHubReadIssue.NETWORK_REQUEST_BUDGET_EXHAUSTED)
        }
        return when (val response = httpExecutor.get(request)) {
            is GitHubHttpResult.Failure -> GitHubReadResult.Failure(response.issue)
            is GitHubHttpResult.Success -> {
                if (response.code in 300..399) {
                    return GitHubReadResult.Failure(GitHubReadIssue.REDIRECT_BLOCKED)
                }
                if (response.code == 404) {
                    return GitHubReadResult.Failure(GitHubReadIssue.NOT_FOUND)
                }
                if (response.isRateLimited()) {
                    return GitHubReadResult.Failure(
                        issue = GitHubReadIssue.RATE_LIMITED,
                        rateLimitResetEpochSeconds = response.rateLimitResetEpochSeconds
                    )
                }
                if (response.code == 403) {
                    return GitHubReadResult.Failure(GitHubReadIssue.ACCESS_DENIED)
                }
                if (response.code !in 200..299) {
                    return GitHubReadResult.Failure(GitHubReadIssue.SERVICE_UNAVAILABLE)
                }
                try {
                    val json = JsonParser.parseString(response.body)
                        .takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?: return GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE)
                    val value = mapper(json)
                        ?: return GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE)
                    GitHubReadResult.Success(value)
                } catch (_: FileLimitExceeded) {
                    GitHubReadResult.Failure(GitHubReadIssue.RESPONSE_TOO_LARGE)
                } catch (_: UnsupportedTextEncoding) {
                    GitHubReadResult.Failure(GitHubReadIssue.UNSUPPORTED_ENCODING)
                } catch (_: RuntimeException) {
                    GitHubReadResult.Failure(GitHubReadIssue.INVALID_RESPONSE)
                }
            }
        }
    }

    private fun repositoryPath(
        repository: GitHubRepositoryRef,
        vararg segments: String
    ): HttpUrl = repositoryUrl(repository, *segments)

    private fun repositoryUrl(
        repository: GitHubRepositoryRef,
        vararg segments: String
    ): HttpUrl {
        val builder = API_BASE.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(repository.owner)
            .addPathSegment(repository.name)
        segments.forEach(builder::addPathSegment)
        return builder.build()
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        return runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
    }

    private fun truncateUtf8(value: String, maxBytes: Int): BoundedText {
        if (value.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) {
            return BoundedText(value, truncated = false)
        }
        var low = 0
        var high = value.length
        while (low < high) {
            val candidate = (low + high + 1) / 2
            val safeCandidate = if (
                candidate < value.length &&
                candidate > 0 &&
                Character.isHighSurrogate(value[candidate - 1])
            ) {
                candidate - 1
            } else {
                candidate
            }
            if (value.substring(0, safeCandidate).toByteArray(StandardCharsets.UTF_8).size <= maxBytes) {
                low = candidate
            } else {
                high = candidate - 1
            }
        }
        var end = low.coerceAtMost(value.length)
        if (end > 0 && end < value.length && Character.isHighSurrogate(value[end - 1])) end--
        return BoundedText(value.substring(0, end), truncated = true)
    }

    private fun isSafeRefValue(value: String): Boolean {
        return value.length in 1..MAX_REF_CHARS &&
            value == value.trim() &&
            value.none { it.isWhitespace() || it.code < 32 }
    }

    private fun isValidSha(value: String): Boolean = SHA_PATTERN.matches(value)

    private data class ResolvedRefCacheKey(
        val repository: GitHubRepositoryRef,
        val ref: String
    )

    private data class CachedBlob(
        val sha: String,
        val text: String,
        val truncated: Boolean,
        val originalSize: Long
    ) {
        fun toTextFile(path: String): GitHubTextFile = GitHubTextFile(
            path = path,
            sha = sha,
            text = text,
            truncated = truncated,
            originalSize = originalSize
        )
    }

    private data class TreePayload(
        val entries: List<GitHubTreeEntry>,
        val reasons: Set<GitHubTruncationReason>,
        val omittedTreeEntries: Int,
        val omittedUnsafeEntries: Int
    ) {
        fun defensiveCopy(): TreePayload = copy(
            entries = entries.map { it.copy() },
            reasons = reasons.toSet()
        )
    }

    private class SnapshotNetworkBudget(maxRequests: Int) {
        private var remainingRequests = maxRequests

        fun tryAcquire(): Boolean {
            if (remainingRequests <= 0) return false
            remainingRequests--
            return true
        }
    }

    private class TimedLruCache<K, V>(
        private val maxEntries: Int,
        private val maxWeight: Long,
        private val ttlMillis: Long,
        private val currentTimeMillis: () -> Long,
        private val weightOf: (V) -> Long
    ) {
        private data class Entry<V>(
            val value: V,
            val expiresAtMillis: Long,
            val weight: Long
        )

        private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)
        private var totalWeight = 0L

        @Synchronized
        fun get(key: K): V? {
            val now = currentTimeMillis()
            removeExpired(now)
            return entries[key]?.value
        }

        @Synchronized
        fun put(key: K, value: V) {
            val now = currentTimeMillis()
            removeExpired(now)
            val weight = weightOf(value).coerceAtLeast(0L)
            if (weight > maxWeight) return
            entries.remove(key)?.let { totalWeight -= it.weight }
            val expiresAt = if (now > Long.MAX_VALUE - ttlMillis) Long.MAX_VALUE else now + ttlMillis
            entries[key] = Entry(value, expiresAt, weight)
            totalWeight += weight
            trimToLimits()
        }

        private fun removeExpired(now: Long) {
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next().value
                if (entry.expiresAtMillis <= now) {
                    totalWeight -= entry.weight
                    iterator.remove()
                }
            }
        }

        private fun trimToLimits() {
            val iterator = entries.entries.iterator()
            while ((entries.size > maxEntries || totalWeight > maxWeight) && iterator.hasNext()) {
                val entry = iterator.next().value
                totalWeight -= entry.weight
                iterator.remove()
            }
        }
    }

    private data class BoundedText(
        val text: String,
        val truncated: Boolean
    )

    private class FileLimitExceeded : RuntimeException()
    private class UnsupportedTextEncoding : RuntimeException()

    companion object {
        private val API_BASE = HttpUrl.Builder()
            .scheme("https")
            .host("api.github.com")
            .build()
        private val SHA_PATTERN = Regex("^[0-9a-f]{40}$")
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val GITHUB_JSON_ACCEPT = "application/vnd.github+json"
        private const val USER_AGENT = "BamaChat-GitHub-Intelligence/1.0"
        private const val BASE64_ENCODING = "base64"
        private const val GIT_TYPE_BLOB = "blob"
        private const val GIT_TYPE_TREE = "tree"
        private val REGULAR_FILE_MODES = setOf("100644", "100755")
        private const val MAX_DESCRIPTION_CHARS = 500
        private const val MAX_REF_CHARS = 100
        private const val MAX_METADATA_CACHE_ENTRIES = 2
        private const val MAX_RESOLVED_REF_CACHE_ENTRIES = 4
        private const val MAX_TREE_CACHE_ENTRIES = 4
    }
}

internal data class GitHubMemoryCacheLimits(
    val ttlMillis: Long = MAX_GITHUB_CACHE_TTL_MILLIS,
    val maxBlobEntries: Int = MAX_GITHUB_BLOB_CACHE_ENTRIES,
    val maxBlobTextBytes: Long = MAX_GITHUB_BLOB_CACHE_TEXT_BYTES
) {
    init {
        require(ttlMillis in 1..MAX_GITHUB_CACHE_TTL_MILLIS)
        require(maxBlobEntries in 1..MAX_GITHUB_BLOB_CACHE_ENTRIES)
        require(maxBlobTextBytes in 1..MAX_GITHUB_BLOB_CACHE_TEXT_BYTES)
    }
}

internal const val MAX_GITHUB_CACHE_TTL_MILLIS = 10L * 60L * 1_000L
internal const val MAX_GITHUB_BLOB_CACHE_ENTRIES = 32
internal const val MAX_GITHUB_BLOB_CACHE_TEXT_BYTES =
    GitHubReadLimits.MAX_SNAPSHOT_TEXT_BYTES.toLong() * 2L

internal sealed interface GitHubHttpResult {
    data class Success(
        val code: Int,
        val body: String,
        val rateLimitLimit: Int? = null,
        val rateLimitRemaining: Int? = null,
        val rateLimitResetEpochSeconds: Long? = null
    ) : GitHubHttpResult
    data class Failure(val issue: GitHubReadIssue) : GitHubHttpResult
}

internal fun interface GitHubReadOnlyHttpExecutor {
    suspend fun get(request: Request): GitHubHttpResult
}

internal class OkHttpGitHubReadOnlyHttpExecutor(
    internal val client: OkHttpClient = createClient(),
    private val callFactory: Call.Factory = client
) : GitHubReadOnlyHttpExecutor {
    override suspend fun get(request: Request): GitHubHttpResult {
        if (request.method != HTTP_GET || request.url.host != ALLOWED_HOST) {
            return GitHubHttpResult.Failure(GitHubReadIssue.REPOSITORY_NOT_ALLOWED)
        }
        val call = callFactory.newCall(request)
        return executeCall(call)
    }

    private suspend fun executeCall(call: Call): GitHubHttpResult =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (!continuation.isActive) return
                    val result = when (error) {
                        is SocketTimeoutException ->
                            GitHubHttpResult.Failure(GitHubReadIssue.REQUEST_TIMED_OUT)
                        is SSLException ->
                            GitHubHttpResult.Failure(GitHubReadIssue.NETWORK_UNAVAILABLE)
                        else ->
                            GitHubHttpResult.Failure(GitHubReadIssue.NETWORK_UNAVAILABLE)
                    }
                    continuation.resume(result)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.toBoundedResult()
                    } catch (_: SocketTimeoutException) {
                        GitHubHttpResult.Failure(GitHubReadIssue.REQUEST_TIMED_OUT)
                    } catch (_: SSLException) {
                        GitHubHttpResult.Failure(GitHubReadIssue.NETWORK_UNAVAILABLE)
                    } catch (_: IOException) {
                        GitHubHttpResult.Failure(GitHubReadIssue.NETWORK_UNAVAILABLE)
                    }
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            })
        }

    private fun Response.toBoundedResult(): GitHubHttpResult = use { response ->
        val rateLimitLimit = response.safeNonNegativeIntHeader(HEADER_RATE_LIMIT_LIMIT)
        val rateLimitRemaining = response.safeNonNegativeIntHeader(HEADER_RATE_LIMIT_REMAINING)
        val rateLimitReset = response.safePositiveLongHeader(HEADER_RATE_LIMIT_RESET)
        val body = response.body ?: return@use GitHubHttpResult.Success(
            code = response.code,
            body = "",
            rateLimitLimit = rateLimitLimit,
            rateLimitRemaining = rateLimitRemaining,
            rateLimitResetEpochSeconds = rateLimitReset
        )
        val source = body.source()
        val buffer = Buffer()
        var totalBytes = 0L
        while (true) {
            val remaining = MAX_HTTP_RESPONSE_BYTES + 1L - totalBytes
            val read = source.read(buffer, minOf(8_192L, remaining))
            if (read == -1L) break
            totalBytes += read
            if (totalBytes > MAX_HTTP_RESPONSE_BYTES) {
                return@use GitHubHttpResult.Failure(GitHubReadIssue.RESPONSE_TOO_LARGE)
            }
        }
        GitHubHttpResult.Success(
            code = response.code,
            body = buffer.readUtf8(),
            rateLimitLimit = rateLimitLimit,
            rateLimitRemaining = rateLimitRemaining,
            rateLimitResetEpochSeconds = rateLimitReset
        )
    }

    companion object {
        private const val ALLOWED_HOST = "api.github.com"
        private const val HTTP_GET = "GET"
        private const val HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit"
        private const val HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining"
        private const val HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset"
        private const val MAX_HTTP_RESPONSE_BYTES = 2L * 1024L * 1024L
        private const val TIMEOUT_SECONDS = 20L

        internal fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}

private fun GitHubHttpResult.Success.isRateLimited(): Boolean {
    if (code == 429) return true
    if (code != 403) return false
    return rateLimitRemaining == 0 || bodyConfirmsRateLimit(body)
}

private fun bodyConfirmsRateLimit(body: String): Boolean {
    val message = runCatching {
        JsonParser.parseString(body)
            .takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.string("message")
    }.getOrNull()?.trim()?.lowercase(Locale.ROOT) ?: return false
    return RATE_LIMIT_MESSAGE_MARKERS.any(message::contains)
}

private fun Response.safeNonNegativeIntHeader(name: String): Int? {
    return header(name)?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
}

private fun Response.safePositiveLongHeader(name: String): Long? {
    return header(name)?.trim()?.toLongOrNull()?.takeIf { it > 0L }
}

private val RATE_LIMIT_MESSAGE_MARKERS = listOf(
    "api rate limit exceeded",
    "rate limit exceeded"
)

private fun JsonObject.string(name: String): String? {
    val element = get(name) ?: return null
    if (element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
    return element.asString
}

private fun JsonObject.long(name: String): Long? {
    val element = get(name) ?: return null
    if (element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
    return runCatching { element.asLong }.getOrNull()
}

private fun JsonObject.boolean(name: String): Boolean? {
    val element = get(name) ?: return null
    if (element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) return null
    return element.asBoolean
}
