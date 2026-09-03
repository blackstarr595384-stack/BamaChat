package com.example.bamachat.shared.core.github

data class GitHubRepositoryRef(
    val owner: String,
    val name: String
)

data class GitHubRepositoryMetadata(
    val repository: GitHubRepositoryRef,
    val defaultBranch: String,
    val description: String?
)

data class GitHubResolvedRef(
    val resolvedRef: String,
    val headCommitSha: String
)

enum class GitHubTreeEntryType {
    FILE,
    DIRECTORY
}

data class GitHubTreeEntry(
    val path: String,
    val type: GitHubTreeEntryType,
    val size: Long?,
    val sha: String
)

data class GitHubTextFile(
    val path: String,
    val sha: String,
    val text: String,
    val truncated: Boolean,
    val originalSize: Long
)

enum class GitHubTruncationReason {
    REMOTE_TREE_TRUNCATED,
    TREE_ENTRY_LIMIT,
    FILE_COUNT_LIMIT,
    NETWORK_REQUEST_LIMIT,
    FILE_SIZE_LIMIT,
    TOTAL_TEXT_LIMIT,
    FILE_TEXT_LIMIT,
    UNSAFE_PATH_SKIPPED
}

data class GitHubTruncationInformation(
    val reasons: Set<GitHubTruncationReason> = emptySet(),
    val omittedTreeEntries: Int = 0,
    val omittedFiles: Int = 0,
    val omittedTextBytes: Long = 0
) {
    val truncated: Boolean
        get() = reasons.isNotEmpty()
}

data class GitHubRepositorySnapshot(
    val repository: GitHubRepositoryRef,
    val resolvedRef: String,
    val headCommitSha: String,
    val defaultBranch: String,
    val repositoryDescription: String?,
    val treeEntries: List<GitHubTreeEntry>,
    val selectedFiles: List<GitHubTextFile>,
    val truncationInformation: GitHubTruncationInformation
)

enum class GitHubAnalysisArea {
    ARCHITECTURE,
    SECURITY,
    ANDROID_UI_UX,
    DESKTOP,
    SHARED_CORE,
    TESTS,
    PERFORMANCE,
    ACCESSIBILITY,
    DOCUMENTATION,
    PROVIDER_SYSTEM,
    AGENTS_EXTENSIONS
}

enum class GitHubProposalCategory {
    ARCHITECTURE,
    SECURITY,
    ANDROID_UI_UX,
    DESKTOP,
    SHARED_CORE,
    TESTS,
    PERFORMANCE,
    ACCESSIBILITY,
    DOCUMENTATION,
    PROVIDER_SYSTEM,
    AGENTS_EXTENSIONS
}

enum class GitHubProposalBenefit {
    LOW,
    MEDIUM,
    HIGH
}

enum class GitHubProposalRisk {
    LOW,
    MEDIUM,
    HIGH
}

enum class GitHubProposalEffort {
    SMALL,
    MEDIUM,
    LARGE
}

enum class GitHubProposalConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class GitHubProposalEvidence(
    val path: String,
    val observation: String
)

data class GitHubImprovementProposal(
    val id: String,
    val title: String,
    val summary: String,
    val category: GitHubProposalCategory,
    val benefit: GitHubProposalBenefit,
    val risk: GitHubProposalRisk,
    val effort: GitHubProposalEffort,
    val confidence: GitHubProposalConfidence,
    val evidence: List<GitHubProposalEvidence>,
    val affectedPaths: List<String>,
    val suggestedChanges: List<String>,
    val testPlan: List<String>,
    val limitations: List<String>
)

data class GitHubRepositoryContext(
    val text: String,
    val includedPaths: List<String>,
    val truncated: Boolean
)

object GitHubReadLimits {
    const val MAX_TREE_ENTRIES = 2_000
    const val MAX_FILES = 12
    const val MAX_COLD_NETWORK_REQUESTS_PER_SNAPSHOT = 15
    const val MAX_ORIGINAL_FILE_BYTES = 250 * 1024
    const val MAX_FILE_TEXT_BYTES = 200 * 1024
    const val MAX_SNAPSHOT_TEXT_BYTES = 1024 * 1024
    const val MAX_PROPOSALS = 6
}
