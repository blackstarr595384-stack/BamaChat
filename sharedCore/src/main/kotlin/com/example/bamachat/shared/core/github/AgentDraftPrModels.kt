package com.example.bamachat.shared.core.github

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

data class AgentDraftPrProposalSelection(
    val proposalId: String,
    val sourceRef: String,
    val sourceCommitSha: String,
    val selectedEvidencePaths: List<String>,
    val requestedAt: Long
)

enum class AgentValidationId {
    SHARED_CORE_TEST,
    ANDROID_UNIT_TEST,
    ANDROID_COMPILE,
    ANDROID_ASSEMBLE,
    DESKTOP_COMPILE,
    DESKTOP_TEST,
    DIFF_CHECK
}

data class AgentImplementationPlan(
    val planId: String,
    val proposalId: String,
    val title: String,
    val summary: String,
    val repository: GitHubRepositoryRef,
    val baseRef: String,
    val baseCommitSha: String,
    val branchName: String,
    val evidencePaths: List<String>,
    val affectedPaths: List<String>,
    val changeSteps: List<String>,
    val validationSteps: List<AgentValidationId>,
    val risk: GitHubProposalRisk,
    val limitations: List<String>,
    val createdAt: Long,
    val expiresAt: Long
)

data class AgentDraftPrRequest(
    val requestId: String,
    val idempotencyKey: String,
    val planId: String,
    val repositoryOwner: String,
    val repositoryName: String,
    val baseRef: String,
    val baseCommitSha: String,
    val branchName: String,
    val approvedPaths: List<String>,
    val approvedChangeSteps: List<String>,
    val approvedValidationSteps: List<AgentValidationId>,
    val explicitUserApproval: Boolean,
    val clientVersion: String
)

enum class AgentDraftPrStatus {
    NOT_STARTED,
    PLAN_READY,
    AWAITING_APPROVAL,
    DRY_RUN_VALIDATING,
    READY_FOR_SERVER_SUBMISSION,
    SERVER_ACCEPTED,
    BRANCH_CREATED,
    CHANGES_APPLIED,
    TESTS_RUNNING,
    TESTS_PASSED,
    DRAFT_PR_CREATED,
    TESTS_FAILED,
    CANCELLED,
    FAILED
}

enum class AgentDraftPrCheckStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED
}

data class AgentDraftPrCheck(
    val validationId: AgentValidationId,
    val status: AgentDraftPrCheckStatus,
    val safeSummary: String?
)

data class AgentDraftPrResult(
    val requestId: String,
    val status: AgentDraftPrStatus,
    val safeMessage: String?,
    val branchName: String,
    val commitSha: String? = null,
    val draftPullRequestNumber: Long? = null,
    val draftPullRequestUrl: String? = null,
    val checks: List<AgentDraftPrCheck> = emptyList(),
    val warnings: List<String> = emptyList(),
    val createdAt: Long
)

enum class AgentDraftPrIssue {
    SERVER_NOT_CONNECTED,
    PLAN_INVALID,
    APPROVAL_REQUIRED,
    REQUEST_REJECTED,
    REQUEST_NOT_FOUND,
    REQUEST_FAILED,
    SERVICE_UNAVAILABLE
}

sealed interface AgentDraftPrGatewayResult<out T> {
    data class Success<T>(val value: T) : AgentDraftPrGatewayResult<T>
    data class Failure(
        val issue: AgentDraftPrIssue,
        val safeMessage: String
    ) : AgentDraftPrGatewayResult<Nothing>
}

interface AgentDraftPrGateway {
    val serverAvailable: Boolean

    suspend fun validatePlan(
        plan: AgentImplementationPlan,
        allowedPaths: Set<String>
    ): AgentDraftPrGatewayResult<AgentImplementationPlan>

    suspend fun submitDraftPrRequest(
        request: AgentDraftPrRequest
    ): AgentDraftPrGatewayResult<AgentDraftPrResult>

    suspend fun getDraftPrStatus(
        requestId: String
    ): AgentDraftPrGatewayResult<AgentDraftPrResult>

    suspend fun cancelDraftPrRequest(
        requestId: String
    ): AgentDraftPrGatewayResult<AgentDraftPrResult>
}

enum class AgentDraftPrPlanIssue {
    REPOSITORY_NOT_ALLOWED,
    REF_NOT_ALLOWED,
    INVALID_PLAN_ID,
    PLAN_ID_CONTENT_MISMATCH,
    INVALID_PROPOSAL_ID,
    INVALID_BASE_SHA,
    INVALID_BRANCH,
    NO_EVIDENCE_PATHS,
    TOO_MANY_EVIDENCE_PATHS,
    AFFECTED_PATH_WITHOUT_EVIDENCE,
    NO_AFFECTED_PATHS,
    TOO_MANY_AFFECTED_PATHS,
    PATH_NOT_IN_SNAPSHOT,
    PROTECTED_PATH,
    EMPTY_CHANGE_STEPS,
    TOO_MANY_CHANGE_STEPS,
    UNSAFE_CHANGE_STEP,
    EMPTY_VALIDATION_STEPS,
    DUPLICATE_VALIDATION_STEP,
    INVALID_VALIDATION_STEPS,
    INVALID_LIMITS,
    EXPIRED
}

sealed interface AgentDraftPrPlanValidation {
    data object Valid : AgentDraftPrPlanValidation
    data class Invalid(val issue: AgentDraftPrPlanIssue) : AgentDraftPrPlanValidation
}

object AgentDraftPrLimits {
    const val MAX_AFFECTED_PATHS = GitHubReadLimits.MAX_FILES
    const val MAX_CHANGE_STEPS = 12
    const val MAX_VALIDATION_STEPS = 7
    const val MAX_LIMITATIONS = 8
    const val MAX_TEXT_CHARS = 1_000
    const val MAX_BRANCH_CHARS = 80
    const val PLAN_LIFETIME_SECONDS = 30 * 60L
}

object AgentDraftPrTextPolicy {
    fun isAllowedSingleLine(value: String): Boolean {
        if (value.isBlank() || value.length > AgentDraftPrLimits.MAX_TEXT_CHARS) return false
        if (value.any { character ->
                character.code in 0..31 || character.code in 127..159 ||
                    character == '\u2028' || character == '\u2029'
            }
        ) {
            return false
        }
        return value.codePoints().noneMatch {
            Character.getType(it) == Character.FORMAT.toInt()
        }
    }
}

object AgentDraftPrIdentifierPolicy {
    private const val PROPOSAL_HASH_HEX_CHARS = 64
    private const val PLAN_HASH_HEX_CHARS = 20
    private const val PROPOSAL_PREFIX = "proposal-"
    private const val COLLISION_SEPARATOR_CHARS = 1
    private val planIdRegex = Regex("^plan-[0-9a-f]{$PLAN_HASH_HEX_CHARS}$")
    private val proposalIdRegex = Regex(
        "^$PROPOSAL_PREFIX[0-9a-f]{$PROPOSAL_HASH_HEX_CHARS}(?:-([1-9][0-9]*))?$"
    )
    private val maxProposalIdChars = PROPOSAL_PREFIX.length +
        PROPOSAL_HASH_HEX_CHARS + COLLISION_SEPARATOR_CHARS +
        GitHubReadLimits.MAX_PROPOSALS.toString().length

    fun isPlanIdAllowed(id: String): Boolean = planIdRegex.matches(id)

    fun isProposalIdAllowed(id: String): Boolean {
        if (id.length > maxProposalIdChars) return false
        val match = proposalIdRegex.matchEntire(id) ?: return false
        val collisionSuffix = match.groupValues[1]
        if (collisionSuffix.isEmpty()) return true
        val collisionNumber = collisionSuffix.toIntOrNull() ?: return false
        return collisionNumber in 2..GitHubReadLimits.MAX_PROPOSALS
    }
}

object AgentDraftPrPathPolicy {
    private val blockedExactNames = setOf(
        "gradle.properties",
        "local.properties",
        "apikeys.properties",
        "keystore.properties",
        "google-services.json"
    )
    private val blockedPrefixes = listOf(
        "service-account",
        "firebase-admin",
        "release-key",
        "release_keys",
        "credentials",
        "secrets",
        "token"
    )
    private val blockedExtensions = setOf(
        "jks",
        "keystore",
        "p12",
        "pfx",
        "pem",
        "key",
        "crt",
        "der"
    )

    fun isAllowed(path: String): Boolean {
        if (!GitHubPathPolicy.isAllowed(path)) return false
        val segments = path.split('/').map { it.lowercase(Locale.ROOT) }
        if (segments.size >= 2 && segments[0] == ".github" && segments[1] == "workflows") {
            return false
        }
        if (segments.any { it == "secrets" || it == "release-keys" }) return false
        val fileName = segments.last()
        if (fileName in blockedExactNames) return false
        if (blockedPrefixes.any(fileName::startsWith)) return false
        return fileName.substringAfterLast('.', "") !in blockedExtensions
    }
}

object AgentDraftPrEvidencePolicy {
    fun canonicalize(paths: List<String>): List<String> = paths.distinct().sorted().toList()

    fun validate(
        affectedPaths: List<String>,
        evidencePaths: List<String>,
        availablePaths: Set<String>
    ): AgentDraftPrPlanIssue? {
        if (evidencePaths.isEmpty()) return AgentDraftPrPlanIssue.NO_EVIDENCE_PATHS
        if (affectedPaths.any { it !in evidencePaths }) {
            return AgentDraftPrPlanIssue.AFFECTED_PATH_WITHOUT_EVIDENCE
        }
        if ((evidencePaths + affectedPaths).any { it !in availablePaths }) {
            return AgentDraftPrPlanIssue.PATH_NOT_IN_SNAPSHOT
        }
        if ((evidencePaths + affectedPaths).any { !AgentDraftPrPathPolicy.isAllowed(it) }) {
            return AgentDraftPrPlanIssue.PROTECTED_PATH
        }
        return null
    }
}

object AgentDraftPrValidationPolicy {
    private val globalBuildConfigurationPaths = setOf(
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle/libs.versions.toml"
    )
    private val allModuleValidationSteps = listOf(
        AgentValidationId.DIFF_CHECK,
        AgentValidationId.SHARED_CORE_TEST,
        AgentValidationId.ANDROID_UNIT_TEST,
        AgentValidationId.ANDROID_COMPILE,
        AgentValidationId.ANDROID_ASSEMBLE,
        AgentValidationId.DESKTOP_COMPILE,
        AgentValidationId.DESKTOP_TEST
    )

    fun requiredFor(affectedPaths: List<String>): List<AgentValidationId> {
        if (affectedPaths.any { it in globalBuildConfigurationPaths }) {
            return allModuleValidationSteps
        }
        val validationSteps = mutableListOf(AgentValidationId.DIFF_CHECK)
        if (affectedPaths.any { it.startsWith("sharedCore/") }) {
            validationSteps += AgentValidationId.SHARED_CORE_TEST
        }
        if (affectedPaths.any { it.startsWith("app/") }) {
            validationSteps += AgentValidationId.ANDROID_UNIT_TEST
            validationSteps += AgentValidationId.ANDROID_COMPILE
            validationSteps += AgentValidationId.ANDROID_ASSEMBLE
        }
        if (affectedPaths.any { it.startsWith("desktopApp/") }) {
            validationSteps += AgentValidationId.DESKTOP_COMPILE
            validationSteps += AgentValidationId.DESKTOP_TEST
        }
        return validationSteps
    }
}

internal object AgentDraftPrCanonicalEncoder {
    private const val STRING_FIELD = 1
    private const val STRING_LIST_FIELD = 2
    private const val LONG_FIELD = 3

    fun encode(schema: String, block: Builder.() -> Unit): ByteArray {
        val builder = Builder()
        builder.string("schema", schema)
        builder.block()
        return builder.toByteArray()
    }

    class Builder internal constructor() {
        private val buffer = ByteArrayOutputStream()
        private val output = DataOutputStream(buffer)

        fun string(field: String, value: String) {
            output.writeByte(STRING_FIELD)
            writeUtf8(field)
            writeUtf8(value)
        }

        fun stringList(field: String, values: List<String>) {
            output.writeByte(STRING_LIST_FIELD)
            writeUtf8(field)
            output.writeInt(values.size)
            values.forEach(::writeUtf8)
        }

        fun long(field: String, value: Long) {
            output.writeByte(LONG_FIELD)
            writeUtf8(field)
            output.writeLong(value)
        }

        internal fun toByteArray(): ByteArray = buffer.toByteArray()

        private fun writeUtf8(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            output.writeInt(bytes.size)
            output.write(bytes)
        }
    }
}

object AgentDraftPrPlanIdentity {
    fun compute(plan: AgentImplementationPlan): String {
        val canonicalEvidencePaths = AgentDraftPrEvidencePolicy.canonicalize(plan.evidencePaths)
        val canonicalAffectedPaths = AgentDraftPrEvidencePolicy.canonicalize(plan.affectedPaths)
        val canonicalContent = AgentDraftPrCanonicalEncoder.encode("agent-draft-pr-plan-v1") {
            string("proposalId", plan.proposalId)
            string("title", plan.title)
            string("summary", plan.summary)
            string("repositoryOwner", plan.repository.owner)
            string("repositoryName", plan.repository.name)
            string("baseRef", plan.baseRef)
            string("baseCommitSha", plan.baseCommitSha.lowercase(Locale.ROOT))
            stringList("evidencePaths", canonicalEvidencePaths)
            stringList("affectedPaths", canonicalAffectedPaths)
            stringList("changeSteps", plan.changeSteps)
            stringList("validationSteps", plan.validationSteps.map { it.name })
            string("risk", plan.risk.name)
            stringList("limitations", plan.limitations)
            long("createdAt", plan.createdAt)
            long("expiresAt", plan.expiresAt)
        }
        return "plan-${AgentDraftPrHash.sha256(canonicalContent).take(20)}"
    }
}

object AgentDraftPrProposalEligibilityPolicy {
    fun validate(
        proposal: GitHubImprovementProposal,
        availablePaths: Set<String>
    ): AgentDraftPrPlanIssue? {
        if (!AgentDraftPrIdentifierPolicy.isProposalIdAllowed(proposal.id)) {
            return AgentDraftPrPlanIssue.INVALID_PROPOSAL_ID
        }
        if (!AgentDraftPrTextPolicy.isAllowedSingleLine(proposal.title) ||
            !AgentDraftPrTextPolicy.isAllowedSingleLine(proposal.summary)
        ) {
            return AgentDraftPrPlanIssue.INVALID_LIMITS
        }
        val evidencePaths = AgentDraftPrEvidencePolicy.canonicalize(
            proposal.evidence.map { it.path }
        )
        val affectedPaths = AgentDraftPrEvidencePolicy.canonicalize(proposal.affectedPaths)
        if (affectedPaths.isEmpty()) return AgentDraftPrPlanIssue.NO_AFFECTED_PATHS
        if (affectedPaths.size > AgentDraftPrLimits.MAX_AFFECTED_PATHS) {
            return AgentDraftPrPlanIssue.TOO_MANY_AFFECTED_PATHS
        }
        if (evidencePaths.isEmpty()) return AgentDraftPrPlanIssue.NO_EVIDENCE_PATHS
        if (evidencePaths.size > AgentDraftPrLimits.MAX_AFFECTED_PATHS) {
            return AgentDraftPrPlanIssue.TOO_MANY_EVIDENCE_PATHS
        }
        AgentDraftPrEvidencePolicy.validate(
            affectedPaths = affectedPaths,
            evidencePaths = evidencePaths,
            availablePaths = availablePaths
        )?.let { return it }
        if (proposal.suggestedChanges.isEmpty()) {
            return AgentDraftPrPlanIssue.EMPTY_CHANGE_STEPS
        }
        if (proposal.suggestedChanges.size > AgentDraftPrLimits.MAX_CHANGE_STEPS) {
            return AgentDraftPrPlanIssue.TOO_MANY_CHANGE_STEPS
        }
        if (proposal.suggestedChanges.any { !AgentDraftPrChangeStepPolicy.isAllowed(it) }) {
            return AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP
        }
        if (proposal.limitations.size > AgentDraftPrLimits.MAX_LIMITATIONS ||
            proposal.limitations.any { !AgentDraftPrTextPolicy.isAllowedSingleLine(it) }
        ) {
            return AgentDraftPrPlanIssue.INVALID_LIMITS
        }
        return null
    }
}

object AgentDraftPrProposalSelectionFactory {
    fun create(
        proposalId: String,
        sourceRef: String,
        sourceCommitSha: String,
        evidencePaths: List<String>,
        requestedAt: Long
    ): AgentDraftPrProposalSelection = AgentDraftPrProposalSelection(
        proposalId = proposalId,
        sourceRef = sourceRef,
        sourceCommitSha = sourceCommitSha,
        selectedEvidencePaths = AgentDraftPrEvidencePolicy.canonicalize(evidencePaths),
        requestedAt = requestedAt
    )
}

object AgentDraftPrBranchPolicy {
    private const val PREFIX = "bamachat-agent/"
    private const val SHORT_PLAN_ID_CHARS = 8
    private const val SEPARATOR_CHARS = 1
    private val suffixRegex = Regex("^[a-z0-9][a-z0-9-]*$")
    private val maxSuffixChars = AgentDraftPrLimits.MAX_BRANCH_CHARS - PREFIX.length
    private val maxSlugChars = maxSuffixChars - SHORT_PLAN_ID_CHARS - SEPARATOR_CHARS

    fun create(planId: String, title: String): String {
        val shortPlanId = planId.removePrefix("plan-")
            .lowercase(Locale.ROOT)
            .filter { it in 'a'..'f' || it.isDigit() }
            .take(SHORT_PLAN_ID_CHARS)
            .ifBlank { "00000000" }
        val slug = title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(maxSlugChars)
            .trimEnd('-')
            .ifBlank { "proposal" }
        return "$PREFIX$shortPlanId-$slug"
    }

    fun isAllowed(branch: String): Boolean {
        if (branch.length > AgentDraftPrLimits.MAX_BRANCH_CHARS) return false
        if (!branch.startsWith(PREFIX)) return false
        val suffix = branch.removePrefix(PREFIX)
        if (suffix.length > maxSuffixChars || !suffixRegex.matches(suffix)) return false
        if (branch.contains("..") || branch.endsWith('.') || branch.endsWith('/')) return false
        return suffix !in setOf("main", "master", "develop", "release") &&
            !suffix.startsWith("main-") &&
            !suffix.startsWith("master-") &&
            !suffix.startsWith("release-")
    }
}

object AgentDraftPrChangeStepPromptContract {
    private val terminalVerbs = listOf(
        "ergänzen",
        "präzisieren",
        "beschreiben",
        "dokumentieren",
        "aktualisieren",
        "anpassen",
        "absichern",
        "verbessern",
        "korrigieren",
        "überarbeiten",
        "vereinfachen",
        "entfernen",
        "ersetzen",
        "refaktorieren",
        "implementieren",
        "validieren",
        "prüfen",
        "testen",
        "strukturieren"
    )
    private val compliantExamples = listOf(
        "Die Dokumentation präzisieren",
        "Die Implementierung absichern",
        "Die Fehlerbehandlung verbessern",
        "Die Validierung aktualisieren",
        "Die Tests ergänzen",
        "README.md aktualisieren",
        "AgentDraftPrChangeStepPolicy absichern"
    )

    val promptText: String = buildString {
        appendLine("VERTRAG FÜR suggestedChanges:")
        appendLine("Jedes Arrayelement ist genau eine einzeilige deutsche, deklarative Änderungsbeschreibung.")
        appendLine("Verwende keinen Imperativ und keine Form wie 'sollte ... werden'.")
        appendLine("Verwende keine Shell-, Git-, Build-, Netzwerk- oder Interpreterbefehle und keine CLI-Optionen.")
        appendLine("Verwende keine Anführungszeichen, Backslashes, Slashes, Dollar- oder Prozentexpansion und keine Shelloperatoren.")
        appendLine("Dateipfade gehören ausschließlich in affectedPaths; suggestedChanges darf nur einen einfachen Dateinamen oder Codebezeichner enthalten.")
        appendLine("Beginne den Satz mit einem sicher unterstützten Subjekt oder einer der garantierten Vorlagen.")
        appendLine("Beende den Satz exakt mit einem dieser deklarativen Verben: ${terminalVerbs.joinToString(", ")}.")
        appendLine("Garantiert policy-konforme Beispiele:")
        compliantExamples.forEach { appendLine(it) }
        append("Mehrere Änderungen müssen als getrennte Arrayelemente ausgegeben werden.")
    }

    internal fun isAllowedTerminalVerb(value: String): Boolean = value in terminalVerbs

    internal fun terminalVerbsForTesting(): List<String> = terminalVerbs.toList()

    internal fun compliantExamplesForTesting(): List<String> = compliantExamples.toList()
}

object AgentDraftPrChangeStepPolicy {
    private val allowedDeclarativePunctuation = setOf(
        ' '.code,
        '.'.code,
        ','.code,
        ':'.code,
        '-'.code,
        '_'.code,
        '('.code,
        ')'.code
    )
    private val declarativeDeterminers = setOf(
        "die", "der", "das", "den", "dem", "ein", "eine", "einen", "einem"
    )
    private val declarativeLeadingAdjectives = setOf(
        "andere",
        "bestehende",
        "betroffene",
        "deklarative",
        "deklarativen",
        "lokale",
        "normale",
        "relevante",
        "sichere",
        "technische",
        "vorhandene",
        "zentrale",
        "zusätzliche",
        "zweite"
    )
    private val declarativeSubjects = setOf(
        "abdeckung",
        "abhängigkeiten",
        "änderungslogik",
        "benutzeroberfläche",
        "code",
        "dokumentation",
        "dokumentationsgrenze",
        "fehlerbehandlung",
        "grenze",
        "implementierung",
        "konfiguration",
        "logik",
        "oberfläche",
        "planvalidierung",
        "policy",
        "richtlinie",
        "schritt",
        "sicherheitsgrenze",
        "struktur",
        "testfälle",
        "testplan",
        "tests",
        "validierung",
        "versionskontrollrichtlinie"
    )
    private val declarativeCompoundSubjectEndings = setOf(
        "abhängigkeiten",
        "auswahl",
        "behandlung",
        "dokumentation",
        "dokumentationsgrenze",
        "grenze",
        "implementierung",
        "konfiguration",
        "logik",
        "plan",
        "policy",
        "prüfung",
        "richtlinie",
        "sicherheitsgrenze",
        "struktur",
        "testplan",
        "tests",
        "validierung",
        "verbot"
    )
    private val standaloneCliOption = Regex("^--?[\\p{L}\\p{N}][\\p{L}\\p{N}-]*$")
    private val repositoryFileSubject = Regex(
        "^[A-Za-z0-9][A-Za-z0-9._-]*\\.(?:kt|kts|java|md|xml|json|toml|yml|yaml|txt|rules|js|ts)$",
        RegexOption.IGNORE_CASE
    )
    private const val COMMAND_START = "(?<![\\p{L}\\p{N}_])"
    private const val COMMAND_END = "(?=\\s|$|[\\[\\](){}:;,.!?/])"
    private val gitCommand = Regex(
        pattern = COMMAND_START + "git" + COMMAND_END,
        option = RegexOption.IGNORE_CASE
    )
    private val executableCommand = Regex(
        pattern = COMMAND_START +
            "(?:gradle(?:w)?(?:\\.(?:bat|cmd))?|mvn(?:w)?(?:\\.(?:bat|cmd))?|" +
            "adb(?:\\.exe)?|powershell(?:\\.exe)?|pwsh(?:\\.exe)?|" +
            "cmd(?:\\.exe)?|bash(?:\\.exe)?|sh(?:\\.exe)?|" +
            "curl(?:\\.exe)?|wget(?:\\.exe)?|wsl(?:\\.exe)?|" +
            "ssh(?:\\.exe)?|scp(?:\\.exe)?|sftp(?:\\.exe)?|ftp(?:\\.exe)?|" +
            "invoke-expression|invoke-webrequest|invoke-restmethod|eval|" +
            "python(?:[0-9]+(?:\\.[0-9]+)*)?(?:\\.exe)?|py(?:\\.exe)?|" +
            "perl(?:\\.exe)?|ruby(?:\\.exe)?|php(?:\\.exe)?|" +
            "zsh(?:\\.exe)?|fish(?:\\.exe)?|" +
            "node(?:\\.exe)?|npm|pnpm|yarn|java|rm|del|copy|move)" + COMMAND_END,
        option = RegexOption.IGNORE_CASE
    )
    private val executableScript = Regex(
        pattern = COMMAND_START +
            "[^\\s\\[\\](){}\\\"']+\\.(?:ps1|bat|cmd|sh)" + COMMAND_END,
        option = RegexOption.IGNORE_CASE
    )
    private val shellOperators = listOf(
        "&&", "||", ";", "|", "`", "${'$'}(", ">", "<", "&"
    )
    private val declarativeConnectors = setOf(
        "als", "an", "auf", "durch", "für", "in", "mit", "ohne", "um", "von", "zur", "zum"
    )
    private val commandArgumentTokens = setOf(
        "build", "compile", "deploy", "echo", "eval", "exec", "install", "publish", "run",
        "script", "status", "test"
    )
    private val argvToken = Regex("^[A-Za-z][A-Za-z0-9._-]*$")

    fun isAllowed(step: String): Boolean {
        if (!AgentDraftPrTextPolicy.isAllowedSingleLine(step)) return false
        if (!usesOnlyDeclarativeCharacters(step)) return false
        if (!matchesDeclarativeSentence(step)) return false
        if (shellOperators.any(step::contains)) return false
        return !gitCommand.containsMatchIn(step) &&
            !executableCommand.containsMatchIn(step) &&
            !executableScript.containsMatchIn(step)
    }

    private fun usesOnlyDeclarativeCharacters(step: String): Boolean {
        return step.codePoints().allMatch { codePoint ->
            Character.isLetterOrDigit(codePoint) || codePoint in allowedDeclarativePunctuation
        }
    }

    private fun matchesDeclarativeSentence(step: String): Boolean {
        val tokens = step.trim().split(Regex(" +"))
        if (tokens.size < 2 || tokens.count(::containsLexicalLetter) < 2) return false
        if (tokens.any(standaloneCliOption::matches)) return false
        val terminalVerb = tokens.last().removeSuffix(".").lowercase(Locale.ROOT)
        if (!AgentDraftPrChangeStepPromptContract.isAllowedTerminalVerb(terminalVerb)) return false

        var subjectIndex = 0
        if (tokens[subjectIndex].lowercase(Locale.ROOT) in declarativeDeterminers) {
            subjectIndex++
        }
        while (subjectIndex < tokens.lastIndex &&
            tokens[subjectIndex].lowercase(Locale.ROOT) in declarativeLeadingAdjectives
        ) {
            subjectIndex++
        }
        if (subjectIndex >= tokens.lastIndex) return false
        if (!isDeclarativeSubject(tokens[subjectIndex])) return false
        return !containsEmbeddedArgvSequence(tokens, subjectIndex)
    }

    private fun containsEmbeddedArgvSequence(tokens: List<String>, subjectIndex: Int): Boolean {
        val body = tokens.subList(subjectIndex + 1, tokens.lastIndex)
        if (body.size < 2) return false
        val normalizedBody = body.map { token -> token.lowercase(Locale.ROOT) }
        if (normalizedBody.first() !in declarativeConnectors &&
            normalizedBody.take(2).all(::isArgvToken)
        ) {
            return true
        }
        return normalizedBody.zipWithNext().any { (executable, argument) ->
            isArgvToken(executable) && argument in commandArgumentTokens
        }
    }

    private fun isArgvToken(token: String): Boolean =
        token !in declarativeDeterminers &&
            token !in declarativeLeadingAdjectives &&
            token !in declarativeConnectors &&
            argvToken.matches(token)

    private fun containsLexicalLetter(token: String): Boolean {
        return token.codePoints().anyMatch(Character::isLetter)
    }

    private fun isDeclarativeSubject(token: String): Boolean {
        val normalized = token.lowercase(Locale.ROOT)
        if (normalized in declarativeSubjects) return true
        if (repositoryFileSubject.matches(token)) return true
        if (isCodeSymbol(token)) return true
        if ('-' !in token) return false
        val compoundEnding = normalized.substringAfterLast('-')
        return compoundEnding in declarativeCompoundSubjectEndings
    }

    private fun isCodeSymbol(token: String): Boolean {
        if (!token.matches(Regex("^[A-Za-z][A-Za-z0-9_]*$"))) return false
        return token.first().isUpperCase() &&
            token.count(Char::isUpperCase) >= 2 &&
            token.any(Char::isLowerCase)
    }
}

object AgentDraftPrUrlPolicy {
    private val urlRegex = Regex(
        "^https://github\\.com/blackstarr595384-stack/BamaChat/pull/([1-9][0-9]*)$"
    )

    fun isAllowed(url: String, draftPullRequestNumber: Long?): Boolean {
        if (draftPullRequestNumber == null || draftPullRequestNumber <= 0L) return false
        val matchedNumber = urlRegex.matchEntire(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return false
        return matchedNumber == draftPullRequestNumber
    }
}

object AgentDraftPrStatusPolicy {
    private val rank = mapOf(
        AgentDraftPrStatus.NOT_STARTED to 0,
        AgentDraftPrStatus.PLAN_READY to 1,
        AgentDraftPrStatus.AWAITING_APPROVAL to 2,
        AgentDraftPrStatus.DRY_RUN_VALIDATING to 3,
        AgentDraftPrStatus.READY_FOR_SERVER_SUBMISSION to 4,
        AgentDraftPrStatus.SERVER_ACCEPTED to 5,
        AgentDraftPrStatus.BRANCH_CREATED to 6,
        AgentDraftPrStatus.CHANGES_APPLIED to 7,
        AgentDraftPrStatus.TESTS_RUNNING to 8,
        AgentDraftPrStatus.TESTS_PASSED to 9,
        AgentDraftPrStatus.DRAFT_PR_CREATED to 10
    )
    private val terminal = setOf(
        AgentDraftPrStatus.DRAFT_PR_CREATED,
        AgentDraftPrStatus.TESTS_FAILED,
        AgentDraftPrStatus.CANCELLED,
        AgentDraftPrStatus.FAILED
    )
    private val cancellable = setOf(
        AgentDraftPrStatus.NOT_STARTED,
        AgentDraftPrStatus.PLAN_READY,
        AgentDraftPrStatus.AWAITING_APPROVAL,
        AgentDraftPrStatus.DRY_RUN_VALIDATING,
        AgentDraftPrStatus.READY_FOR_SERVER_SUBMISSION
    )

    fun canTransition(from: AgentDraftPrStatus, to: AgentDraftPrStatus): Boolean {
        if (from == to) return true
        if (from in terminal) return false
        if (to == AgentDraftPrStatus.CANCELLED) return from in cancellable
        if (to == AgentDraftPrStatus.FAILED) return true
        if (to == AgentDraftPrStatus.TESTS_FAILED) {
            return from == AgentDraftPrStatus.TESTS_RUNNING
        }
        val fromRank = rank[from] ?: return false
        val toRank = rank[to] ?: return false
        return toRank == fromRank + 1
    }
}

object AgentDraftPrPlanPolicy {
    private val shaRegex = Regex("^[0-9a-f]{40}$")

    fun validate(
        plan: AgentImplementationPlan,
        allowedPaths: Set<String>,
        nowEpochSeconds: Long
    ): AgentDraftPrPlanValidation {
        if (!GitHubRepositoryPolicy.isAllowed(plan.repository)) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.REPOSITORY_NOT_ALLOWED)
        }
        if (!GitHubRepositoryPolicy.isAllowedRef(plan.baseRef)) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.REF_NOT_ALLOWED)
        }
        if (!AgentDraftPrIdentifierPolicy.isPlanIdAllowed(plan.planId)) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_PLAN_ID)
        }
        if (!AgentDraftPrIdentifierPolicy.isProposalIdAllowed(plan.proposalId)) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_PROPOSAL_ID)
        }
        if (!AgentDraftPrTextPolicy.isAllowedSingleLine(plan.title) ||
            !AgentDraftPrTextPolicy.isAllowedSingleLine(plan.summary) ||
            plan.limitations.size > AgentDraftPrLimits.MAX_LIMITATIONS ||
            plan.limitations.any { !AgentDraftPrTextPolicy.isAllowedSingleLine(it) }
        ) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_LIMITS)
        }
        if (!shaRegex.matches(plan.baseCommitSha)) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_BASE_SHA)
        }
        if (!AgentDraftPrBranchPolicy.isAllowed(plan.branchName) ||
            plan.branchName != AgentDraftPrBranchPolicy.create(plan.planId, plan.title)
        ) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_BRANCH)
        }
        if (plan.affectedPaths.isEmpty()) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.NO_AFFECTED_PATHS)
        }
        if (plan.affectedPaths.size > AgentDraftPrLimits.MAX_AFFECTED_PATHS) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.TOO_MANY_AFFECTED_PATHS)
        }
        if (plan.evidencePaths.size > AgentDraftPrLimits.MAX_AFFECTED_PATHS) {
            return AgentDraftPrPlanValidation.Invalid(
                AgentDraftPrPlanIssue.TOO_MANY_EVIDENCE_PATHS
            )
        }
        if (plan.affectedPaths.distinct().size != plan.affectedPaths.size ||
            plan.affectedPaths != plan.affectedPaths.sorted() ||
            plan.evidencePaths.distinct().size != plan.evidencePaths.size ||
            plan.evidencePaths != plan.evidencePaths.sorted()
        ) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_LIMITS)
        }
        AgentDraftPrEvidencePolicy.validate(
            affectedPaths = plan.affectedPaths,
            evidencePaths = plan.evidencePaths,
            availablePaths = allowedPaths
        )?.let { issue ->
            return AgentDraftPrPlanValidation.Invalid(issue)
        }
        if (plan.changeSteps.isEmpty()) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.EMPTY_CHANGE_STEPS)
        }
        if (plan.changeSteps.size > AgentDraftPrLimits.MAX_CHANGE_STEPS) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.TOO_MANY_CHANGE_STEPS)
        }
        if (plan.changeSteps.any { !AgentDraftPrChangeStepPolicy.isAllowed(it) }) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP)
        }
        if (plan.validationSteps.isEmpty()) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.EMPTY_VALIDATION_STEPS)
        }
        if (plan.validationSteps.size > AgentDraftPrLimits.MAX_VALIDATION_STEPS ||
            plan.validationSteps.distinct().size != plan.validationSteps.size
        ) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.DUPLICATE_VALIDATION_STEP)
        }
        if (plan.validationSteps != AgentDraftPrValidationPolicy.requiredFor(plan.affectedPaths)) {
            return AgentDraftPrPlanValidation.Invalid(
                AgentDraftPrPlanIssue.INVALID_VALIDATION_STEPS
            )
        }
        if (nowEpochSeconds <= 0L ||
            plan.createdAt <= 0L ||
            plan.createdAt > nowEpochSeconds ||
            plan.expiresAt <= plan.createdAt ||
            plan.expiresAt <= nowEpochSeconds ||
            plan.expiresAt - plan.createdAt >
            AgentDraftPrLimits.PLAN_LIFETIME_SECONDS
        ) {
            return AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.EXPIRED)
        }
        if (plan.planId != AgentDraftPrPlanIdentity.compute(plan)) {
            return AgentDraftPrPlanValidation.Invalid(
                AgentDraftPrPlanIssue.PLAN_ID_CONTENT_MISMATCH
            )
        }
        return AgentDraftPrPlanValidation.Valid
    }
}

sealed interface AgentImplementationPlanResult {
    data class Success(val plan: AgentImplementationPlan) : AgentImplementationPlanResult
    data class Failure(val issue: AgentDraftPrPlanIssue) : AgentImplementationPlanResult
}

object AgentImplementationPlanFactory {
    fun create(
        proposal: GitHubImprovementProposal,
        repository: GitHubRepositoryRef,
        baseRef: String,
        baseCommitSha: String,
        availablePaths: Set<String>,
        nowEpochSeconds: Long
    ): AgentImplementationPlanResult {
        AgentDraftPrProposalEligibilityPolicy.validate(
            proposal = proposal,
            availablePaths = availablePaths
        )?.let { issue ->
            return AgentImplementationPlanResult.Failure(issue)
        }
        val evidencePaths = AgentDraftPrEvidencePolicy.canonicalize(
            proposal.evidence.map { it.path }
        )
        val affectedPaths = AgentDraftPrEvidencePolicy.canonicalize(proposal.affectedPaths)
        val changeSteps = proposal.suggestedChanges.distinct()
        val validations = AgentDraftPrValidationPolicy.requiredFor(affectedPaths)
        val limitations = proposal.limitations.distinct()
        val normalizedBaseCommitSha = baseCommitSha.lowercase(Locale.ROOT)
        val unboundPlan = AgentImplementationPlan(
            planId = "",
            proposalId = proposal.id,
            title = proposal.title,
            summary = proposal.summary,
            repository = repository,
            baseRef = baseRef,
            baseCommitSha = normalizedBaseCommitSha,
            branchName = "",
            evidencePaths = evidencePaths.toList(),
            affectedPaths = affectedPaths,
            changeSteps = changeSteps,
            validationSteps = validations,
            risk = proposal.risk,
            limitations = limitations,
            createdAt = nowEpochSeconds,
            expiresAt = nowEpochSeconds + AgentDraftPrLimits.PLAN_LIFETIME_SECONDS
        )
        val planId = AgentDraftPrPlanIdentity.compute(unboundPlan)
        val plan = unboundPlan.copy(
            planId = planId,
            branchName = AgentDraftPrBranchPolicy.create(planId, proposal.title)
        )
        return when (
            val validation = AgentDraftPrPlanPolicy.validate(
                plan,
                availablePaths,
                nowEpochSeconds
            )
        ) {
            AgentDraftPrPlanValidation.Valid -> AgentImplementationPlanResult.Success(plan)
            is AgentDraftPrPlanValidation.Invalid -> AgentImplementationPlanResult.Failure(validation.issue)
        }
    }

}

sealed interface AgentDraftPrRequestResult {
    data class Success(val request: AgentDraftPrRequest) : AgentDraftPrRequestResult
    data class Failure(val issue: AgentDraftPrIssue) : AgentDraftPrRequestResult
}

object AgentDraftPrRequestFactory {
    fun create(
        plan: AgentImplementationPlan,
        allowedPaths: Set<String>,
        explicitApproval: Boolean,
        clientVersion: String,
        nowEpochSeconds: Long
    ): AgentDraftPrRequestResult {
        if (!explicitApproval) {
            return AgentDraftPrRequestResult.Failure(AgentDraftPrIssue.APPROVAL_REQUIRED)
        }
        if (clientVersion.isBlank() || clientVersion.length > 100) {
            return AgentDraftPrRequestResult.Failure(AgentDraftPrIssue.PLAN_INVALID)
        }
        if (AgentDraftPrPlanPolicy.validate(plan, allowedPaths, nowEpochSeconds) !=
            AgentDraftPrPlanValidation.Valid
        ) {
            return AgentDraftPrRequestResult.Failure(AgentDraftPrIssue.PLAN_INVALID)
        }
        val canonical = AgentDraftPrCanonicalEncoder.encode("agent-draft-pr-request-v1") {
            string("repositoryOwner", plan.repository.owner)
            string("repositoryName", plan.repository.name)
            string("baseRef", plan.baseRef)
            string("baseCommitSha", plan.baseCommitSha)
            string("planId", plan.planId)
            string("branchName", plan.branchName)
            stringList("affectedPaths", plan.affectedPaths)
            stringList("changeSteps", plan.changeSteps)
            stringList("validationSteps", plan.validationSteps.map { it.name })
        }
        val digest = AgentDraftPrHash.sha256(canonical)
        return AgentDraftPrRequestResult.Success(
            AgentDraftPrRequest(
                requestId = "request-${digest.take(20)}",
                idempotencyKey = "idem-$digest",
                planId = plan.planId,
                repositoryOwner = plan.repository.owner,
                repositoryName = plan.repository.name,
                baseRef = plan.baseRef,
                baseCommitSha = plan.baseCommitSha,
                branchName = plan.branchName,
                approvedPaths = plan.affectedPaths.toList(),
                approvedChangeSteps = plan.changeSteps.toList(),
                approvedValidationSteps = plan.validationSteps.toList(),
                explicitUserApproval = true,
                clientVersion = clientVersion
            )
        )
    }
}

internal object AgentDraftPrHash {
    fun sha256(value: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
    }
}
