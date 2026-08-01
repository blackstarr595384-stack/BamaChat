package com.example.bamachat.data.github

import com.example.bamachat.shared.core.github.GitHubImprovementProposal
import com.example.bamachat.shared.core.github.GitHubPathPolicy
import com.example.bamachat.shared.core.github.GitHubProposalBenefit
import com.example.bamachat.shared.core.github.GitHubProposalCategory
import com.example.bamachat.shared.core.github.GitHubProposalConfidence
import com.example.bamachat.shared.core.github.GitHubProposalEffort
import com.example.bamachat.shared.core.github.GitHubProposalEvidence
import com.example.bamachat.shared.core.github.GitHubProposalRisk
import com.example.bamachat.shared.core.github.GitHubReadLimits
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

enum class GitHubProposalParseIssue {
    EMPTY_RESPONSE,
    INVALID_JSON,
    MISSING_REQUIRED_FIELDS,
    UNKNOWN_EVIDENCE_PATH,
    AMBIGUOUS_JSON_PAYLOAD,
    RESPONSE_TOO_LARGE
}

sealed interface GitHubProposalParseResult {
    data class Success(val proposals: List<GitHubImprovementProposal>) : GitHubProposalParseResult
    data class Failure(val issue: GitHubProposalParseIssue) : GitHubProposalParseResult
}

internal sealed interface GitHubJsonPayloadExtractionResult {
    data class Success(val payload: String) : GitHubJsonPayloadExtractionResult
    data object EmptyResponse : GitHubJsonPayloadExtractionResult
    data object InvalidJson : GitHubJsonPayloadExtractionResult
    data object AmbiguousJsonPayload : GitHubJsonPayloadExtractionResult
    data object ResponseTooLarge : GitHubJsonPayloadExtractionResult
}

internal class GitHubJsonPayloadExtractor {
    fun extract(rawResponse: String): GitHubJsonPayloadExtractionResult {
        if (rawResponse.toByteArray(StandardCharsets.UTF_8).size > MAX_RAW_RESPONSE_BYTES) {
            return GitHubJsonPayloadExtractionResult.ResponseTooLarge
        }
        if (rawResponse.isBlank()) {
            return GitHubJsonPayloadExtractionResult.EmptyResponse
        }

        var validCandidate: PayloadCandidate? = null
        var index = 0
        while (index < rawResponse.length) {
            if (rawResponse[index] != '{' && rawResponse[index] != '[') {
                index++
                continue
            }
            val startIndex = index
            val stack = ArrayDeque<Char>()
            var inString = false
            var escaping = false
            var endInclusive = -1
            while (index < rawResponse.length) {
                val character = rawResponse[index]
                if (inString) {
                    when {
                        escaping -> escaping = false
                        character == '\\' -> escaping = true
                        character == '"' -> inString = false
                    }
                } else {
                    when (character) {
                        '"' -> inString = true
                        '{', '[' -> stack.addLast(character)
                        '}', ']' -> {
                            val expected = if (character == '}') '{' else '['
                            if (stack.removeLastOrNull() != expected) {
                                return GitHubJsonPayloadExtractionResult.InvalidJson
                            }
                            if (stack.isEmpty()) {
                                endInclusive = index
                                index++
                                break
                            }
                        }
                    }
                }
                index++
            }
            if (endInclusive < 0 || inString || stack.isNotEmpty()) {
                return GitHubJsonPayloadExtractionResult.InvalidJson
            }
            val payload = rawResponse.substring(startIndex, endInclusive + 1)
            val root = parseStrictJson(payload)
            if (root != null && (root.isJsonObject || root.isJsonArray)) {
                if (validCandidate != null) {
                    return GitHubJsonPayloadExtractionResult.AmbiguousJsonPayload
                }
                validCandidate = PayloadCandidate(startIndex, endInclusive)
            }
        }
        val candidate = validCandidate ?: return GitHubJsonPayloadExtractionResult.InvalidJson
        val surroundingBytes = rawResponse.substring(0, candidate.startIndex)
            .toByteArray(StandardCharsets.UTF_8).size +
            rawResponse.substring(candidate.endInclusive + 1)
                .toByteArray(StandardCharsets.UTF_8).size
        if (surroundingBytes > MAX_SURROUNDING_TEXT_BYTES) {
            return GitHubJsonPayloadExtractionResult.InvalidJson
        }
        return GitHubJsonPayloadExtractionResult.Success(
            rawResponse.substring(candidate.startIndex, candidate.endInclusive + 1)
        )
    }

    private data class PayloadCandidate(
        val startIndex: Int,
        val endInclusive: Int
    )

    companion object {
        internal const val MAX_RAW_RESPONSE_BYTES = 256 * 1024
        private const val MAX_SURROUNDING_TEXT_BYTES = 4 * 1024
    }
}

class GitHubImprovementProposalParser internal constructor(
    private val payloadExtractor: GitHubJsonPayloadExtractor = GitHubJsonPayloadExtractor()
) {
    fun parse(
        rawResponse: String,
        allowedEvidencePaths: Set<String>
    ): GitHubProposalParseResult {
        val payload = when (val extraction = payloadExtractor.extract(rawResponse)) {
            is GitHubJsonPayloadExtractionResult.Success -> extraction.payload
            GitHubJsonPayloadExtractionResult.EmptyResponse ->
                return GitHubProposalParseResult.Failure(GitHubProposalParseIssue.EMPTY_RESPONSE)
            GitHubJsonPayloadExtractionResult.InvalidJson ->
                return GitHubProposalParseResult.Failure(GitHubProposalParseIssue.INVALID_JSON)
            GitHubJsonPayloadExtractionResult.AmbiguousJsonPayload ->
                return GitHubProposalParseResult.Failure(
                    GitHubProposalParseIssue.AMBIGUOUS_JSON_PAYLOAD
                )
            GitHubJsonPayloadExtractionResult.ResponseTooLarge ->
                return GitHubProposalParseResult.Failure(GitHubProposalParseIssue.RESPONSE_TOO_LARGE)
        }
        val root = parseStrictJson(payload)
            ?: return GitHubProposalParseResult.Failure(GitHubProposalParseIssue.INVALID_JSON)
        val items = root.proposalArray()
            ?: return GitHubProposalParseResult.Failure(GitHubProposalParseIssue.INVALID_JSON)
        if (items.isEmpty) return GitHubProposalParseResult.Success(emptyList())
        val proposals = mutableListOf<GitHubImprovementProposal>()
        val duplicateKeys = mutableSetOf<String>()
        val assignedIds = mutableSetOf<String>()
        var missingFields = false
        var unknownPath = false

        items.forEach { element ->
            val json = element.takeIf { it.isJsonObject }?.asJsonObject ?: run {
                missingFields = true
                return@forEach
            }
            when (val parsed = parseProposal(json, allowedEvidencePaths)) {
                is ProposalItemResult.Valid -> {
                    val duplicateKey = buildCanonicalKey(parsed.proposal)
                    if (
                        duplicateKeys.add(duplicateKey) &&
                        proposals.size < GitHubReadLimits.MAX_PROPOSALS
                    ) {
                        val internalId = uniqueStableId(duplicateKey, assignedIds)
                        proposals += parsed.proposal.copy(id = internalId)
                    }
                }
                ProposalItemResult.MissingFields -> missingFields = true
                ProposalItemResult.UnknownPath -> unknownPath = true
            }
        }
        if (unknownPath) {
            return GitHubProposalParseResult.Failure(
                GitHubProposalParseIssue.UNKNOWN_EVIDENCE_PATH
            )
        }
        if (missingFields) {
            return GitHubProposalParseResult.Failure(
                GitHubProposalParseIssue.MISSING_REQUIRED_FIELDS
            )
        }
        return GitHubProposalParseResult.Success(proposals)
    }

    private fun parseProposal(
        json: JsonObject,
        allowedEvidencePaths: Set<String>
    ): ProposalItemResult {
        val title = json.safeText("title", MAX_TITLE_CHARS) ?: return ProposalItemResult.MissingFields
        val summary = json.safeText("summary", MAX_SUMMARY_CHARS) ?: return ProposalItemResult.MissingFields
        val category = json.enumValue<GitHubProposalCategory>("category")
            ?: return ProposalItemResult.MissingFields
        val benefit = json.enumValue<GitHubProposalBenefit>("benefit")
            ?: return ProposalItemResult.MissingFields
        val risk = json.enumValue<GitHubProposalRisk>("risk")
            ?: return ProposalItemResult.MissingFields
        val effort = json.enumValue<GitHubProposalEffort>("effort")
            ?: return ProposalItemResult.MissingFields
        val confidence = json.enumValue<GitHubProposalConfidence>("confidence")
            ?: return ProposalItemResult.MissingFields
        val evidence = json.evidenceItems() ?: return ProposalItemResult.MissingFields
        val affectedPaths = json.safePathList("affectedPaths") ?: return ProposalItemResult.MissingFields
        val suggestedChanges = json.safeTextList("suggestedChanges", MAX_LIST_ITEM_CHARS)
            ?: return ProposalItemResult.MissingFields
        val testPlan = json.safeTextList("testPlan", MAX_LIST_ITEM_CHARS)
            ?: return ProposalItemResult.MissingFields
        val limitations = json.safeTextList("limitations", MAX_LIST_ITEM_CHARS)
            ?: return ProposalItemResult.MissingFields
        val referencedPaths = evidence.map { it.path } + affectedPaths
        if (referencedPaths.any { it !in allowedEvidencePaths || !GitHubPathPolicy.isAllowed(it) }) {
            return ProposalItemResult.UnknownPath
        }
        val proposal = GitHubImprovementProposal(
            id = "",
            title = title,
            summary = summary,
            category = category,
            benefit = benefit,
            risk = risk,
            effort = effort,
            confidence = confidence,
            evidence = evidence,
            affectedPaths = affectedPaths,
            suggestedChanges = suggestedChanges,
            testPlan = testPlan,
            limitations = limitations
        )
        return ProposalItemResult.Valid(proposal)
    }

    private fun JsonObject.evidenceItems(): List<GitHubProposalEvidence>? {
        val array = array("evidence") ?: return null
        val evidence = array.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val path = item.string("path")
                ?.takeIf { it.isNotBlank() && it == it.trim() }
                ?: return@mapNotNull null
            val observation = item.safeText("observation", MAX_LIST_ITEM_CHARS) ?: return@mapNotNull null
            GitHubProposalEvidence(path, observation)
        }
        return evidence
            .distinctBy { it.path to it.observation }
            .sortedWith(compareBy({ it.path }, { it.observation }))
            .take(MAX_ITEMS_PER_LIST)
            .takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.safePathList(name: String): List<String>? {
        val paths = array(name)?.mapNotNull { element ->
            element.stringValue()?.takeIf { it.isNotBlank() && it == it.trim() }
        }.orEmpty()
        return paths
            .distinct()
            .sorted()
            .take(MAX_ITEMS_PER_LIST)
            .takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.safeTextList(name: String, maxChars: Int): List<String>? {
        val values = array(name)?.mapNotNull { element ->
            element.stringValue()?.sanitizeForDisplay(maxChars)?.takeIf { it.isNotBlank() }
        }.orEmpty()
        return values
            .distinct()
            .take(MAX_ITEMS_PER_LIST)
            .takeIf { it.isNotEmpty() }
    }

    private inline fun <reified T : Enum<T>> JsonObject.enumValue(name: String): T? {
        val normalized = string(name)
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.replace('-', '_')
            ?.replace(' ', '_')
            ?: return null
        return enumValues<T>().firstOrNull { it.name == normalized }
    }

    private fun JsonObject.safeText(name: String, maxChars: Int): String? {
        return string(name)?.sanitizeForDisplay(maxChars)?.takeIf { it.isNotBlank() }
    }

    private fun String.sanitizeForDisplay(maxChars: Int): String {
        return this
            .replace(CONTROL_CHARS, " ")
            .replace(PRIVATE_KEY_BLOCK, "[Zugangsdaten entfernt]")
            .replace(DATA_URL, "[Daten entfernt]")
            .replace(WEB_URL, "[Link entfernt]")
            .replace(AUTHORIZATION_ASSIGNMENT, "[Zugangsdaten entfernt]")
            .replace(BEARER_CREDENTIAL, "[Zugangsdaten entfernt]")
            .replace(GITHUB_PAT, "[Zugangsdaten entfernt]")
            .replace(SK_API_KEY, "[Zugangsdaten entfernt]")
            .replace(GOOGLE_AI_KEY, "[Zugangsdaten entfernt]")
            .replace(GENERIC_CREDENTIAL_ASSIGNMENT, "[Zugangsdaten entfernt]")
            .replace(WHITESPACE, " ")
            .trim()
            .take(maxChars)
    }

    private fun uniqueStableId(
        canonicalKey: String,
        assignedIds: MutableSet<String>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalKey.toByteArray(StandardCharsets.UTF_8))
        val baseId = "proposal-" + digest.joinToString("") { "%02x".format(it) }
        var candidate = baseId
        var suffix = 2
        while (!assignedIds.add(candidate)) {
            candidate = "$baseId-$suffix"
            suffix++
        }
        return candidate
    }

    private fun buildCanonicalKey(proposal: GitHubImprovementProposal): String = buildString {
        fun add(value: String) {
            append(value.length)
            append(':')
            append(value)
            append('|')
        }

        add(proposal.category.name)
        add(proposal.title.lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim())
        add(proposal.summary)
        add(proposal.benefit.name)
        add(proposal.risk.name)
        add(proposal.effort.name)
        add(proposal.confidence.name)
        proposal.evidence
            .sortedWith(compareBy({ it.path }, { it.observation }))
            .forEach {
                add(it.path)
                add(it.observation)
            }
        proposal.affectedPaths.sorted().forEach(::add)
        proposal.suggestedChanges.sorted().forEach(::add)
        proposal.testPlan.sorted().forEach(::add)
        proposal.limitations.sorted().forEach(::add)
    }

    private sealed interface ProposalItemResult {
        data class Valid(val proposal: GitHubImprovementProposal) : ProposalItemResult
        data object MissingFields : ProposalItemResult
        data object UnknownPath : ProposalItemResult
    }

    companion object {
        private const val MAX_TITLE_CHARS = 160
        private const val MAX_SUMMARY_CHARS = 1_200
        private const val MAX_LIST_ITEM_CHARS = 1_000
        internal const val MAX_ITEMS_PER_LIST = 20
        private val CONTROL_CHARS = Regex("[\\u0000-\\u001F\\u007F]")
        private val WHITESPACE = Regex("\\s+")
        private val WEB_URL = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        private val DATA_URL = Regex("data:[^\\s]+", RegexOption.IGNORE_CASE)
        private val PRIVATE_KEY_BLOCK = Regex(
            "-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----[\\s\\S]*?" +
                "-----END(?: [A-Z0-9]+)? PRIVATE KEY-----",
            RegexOption.IGNORE_CASE
        )
        private val AUTHORIZATION_ASSIGNMENT = Regex(
            "\\bAuthorization\\b\\s*[:=]\\s*[^\\r\\n,;]{8,}",
            RegexOption.IGNORE_CASE
        )
        private val BEARER_CREDENTIAL = Regex(
            "\\bBearer\\s+[0-9A-Za-z._~+/=-]{8,}",
            RegexOption.IGNORE_CASE
        )
        private val GITHUB_PAT = Regex(
            "\\b(?:github_pat_[0-9A-Za-z_]{20,}|gh[pousr]_[0-9A-Za-z]{20,})\\b",
            RegexOption.IGNORE_CASE
        )
        private val SK_API_KEY = Regex(
            "\\bsk-[0-9A-Za-z_-]{16,}\\b",
            RegexOption.IGNORE_CASE
        )
        private val GOOGLE_AI_KEY = Regex("\\bAIza[0-9A-Za-z_-]{20,}\\b")
        private val GENERIC_CREDENTIAL_ASSIGNMENT = Regex(
            "\\b(?:api[_-]?key|access[_-]?token|auth[_-]?token|token|secret|client[_-]?secret)" +
                "\\b\\s*[\"']?\\s*[:=]\\s*[\"']?[0-9A-Za-z._~+/=-]{16,}[\"']?",
            RegexOption.IGNORE_CASE
        )
    }
}

private fun parseStrictJson(payload: String): JsonElement? {
    return runCatching {
        val reader = JsonReader(StringReader(payload)).apply {
            strictness = Strictness.STRICT
        }
        JsonParser.parseReader(reader).also {
            check(reader.peek() == JsonToken.END_DOCUMENT)
        }
    }.getOrNull()
}

private fun JsonElement.proposalArray(): JsonArray? {
    return when {
        isJsonArray -> asJsonArray
        isJsonObject -> asJsonObject.getAsJsonArray("proposals")
        else -> null
    }
}

private fun JsonObject.array(name: String): JsonArray? {
    val element = get(name) ?: return null
    return element.takeIf { it.isJsonArray }?.asJsonArray
}

private fun JsonObject.string(name: String): String? = get(name)?.stringValue()

private fun JsonElement.stringValue(): String? {
    return takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString
}
