package com.example.bamachat.shared.core.github

enum class GitHubReadIssue {
    REPOSITORY_NOT_ALLOWED,
    REF_NOT_ALLOWED,
    PATH_NOT_ALLOWED,
    NOT_FOUND,
    RATE_LIMITED,
    ACCESS_DENIED,
    REDIRECT_BLOCKED,
    NETWORK_REQUEST_BUDGET_EXHAUSTED,
    REQUEST_TIMED_OUT,
    NETWORK_UNAVAILABLE,
    RESPONSE_TOO_LARGE,
    INVALID_RESPONSE,
    UNSUPPORTED_ENCODING,
    SERVICE_UNAVAILABLE
}

sealed interface GitHubReadResult<out T> {
    data class Success<T>(val value: T) : GitHubReadResult<T>
    data class Failure(
        val issue: GitHubReadIssue,
        val rateLimitResetEpochSeconds: Long? = null
    ) : GitHubReadResult<Nothing>
}

interface GitHubReadOnlyRepositoryGateway {
    suspend fun readRepositoryMetadata(
        repository: GitHubRepositoryRef
    ): GitHubReadResult<GitHubRepositoryMetadata>

    suspend fun resolveRef(
        repository: GitHubRepositoryRef,
        ref: String
    ): GitHubReadResult<GitHubResolvedRef>

    suspend fun readTree(
        repository: GitHubRepositoryRef,
        resolvedRef: GitHubResolvedRef
    ): GitHubReadResult<List<GitHubTreeEntry>>

    suspend fun readTextFile(
        repository: GitHubRepositoryRef,
        resolvedRef: GitHubResolvedRef,
        path: String
    ): GitHubReadResult<GitHubTextFile>

    suspend fun readSnapshot(
        repository: GitHubRepositoryRef,
        ref: String,
        analysisArea: GitHubAnalysisArea
    ): GitHubReadResult<GitHubRepositorySnapshot>
}
