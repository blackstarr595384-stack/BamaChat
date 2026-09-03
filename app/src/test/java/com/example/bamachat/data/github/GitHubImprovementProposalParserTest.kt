package com.example.bamachat.data.github

import com.example.bamachat.shared.core.github.GitHubProposalBenefit
import com.example.bamachat.shared.core.github.GitHubProposalCategory
import com.example.bamachat.shared.core.github.GitHubReadLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubImprovementProposalParserTest {
    private val parser = GitHubImprovementProposalParser()
    private val allowedPaths = setOf("README.md", "app/src/main/App.kt")

    @Test
    fun validStructuredResponseIsParsed() {
        val result = parser.parse(validResponse(), allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
        val proposal = (result as GitHubProposalParseResult.Success).proposals.single()
        assertEquals("Architekturgrenze schärfen", proposal.title)
        assertEquals(GitHubProposalCategory.ARCHITECTURE, proposal.category)
        assertEquals(GitHubProposalBenefit.HIGH, proposal.benefit)
        assertEquals(listOf("README.md"), proposal.affectedPaths)
        assertEquals("README.md", proposal.evidence.single().path)
    }

    @Test
    fun validProposalWithoutModelIdIsAccepted() {
        val result = parser.parse(
            """{"proposals":[${validProposal(id = null)}]}""",
            allowedPaths
        )

        assertTrue(result is GitHubProposalParseResult.Success)
        val proposal = (result as GitHubProposalParseResult.Success).proposals.single()
        assertTrue(SAFE_INTERNAL_ID.matches(proposal.id))
    }

    @Test
    fun emptyObjectProposalListIsAValidEmptyResult() {
        assertEquals(
            GitHubProposalParseResult.Success(emptyList()),
            parser.parse("""{"proposals":[]}""", allowedPaths)
        )
    }

    @Test
    fun directEmptyArrayIsAValidEmptyResult() {
        assertEquals(
            GitHubProposalParseResult.Success(emptyList()),
            parser.parse("[]", allowedPaths)
        )
    }

    @Test
    fun missingRequiredFieldsAreRejected() {
        val result = parser.parse(
            """{"proposals":[{"title":"Unvollständig"}]}""",
            allowedPaths
        )

        assertEquals(
            GitHubProposalParseResult.Failure(GitHubProposalParseIssue.MISSING_REQUIRED_FIELDS),
            result
        )
    }

    @Test
    fun unstructuredResponseIsRejected() {
        val result = parser.parse("Hier sind meine Vorschläge.", allowedPaths)

        assertEquals(
            GitHubProposalParseResult.Failure(GitHubProposalParseIssue.INVALID_JSON),
            result
        )
    }

    @Test
    fun proposalsAreLimitedToTwelve() {
        val proposals = (1..20).joinToString(",") { index ->
            validProposal(title = "Vorschlag $index", id = "proposal-$index")
        }
        val result = parser.parse("""{"proposals":[$proposals]}""", allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
        assertEquals(
            GitHubReadLimits.MAX_PROPOSALS,
            (result as GitHubProposalParseResult.Success).proposals.size
        )
    }

    @Test
    fun duplicateProposalsAreRemoved() {
        val response = """{"proposals":[${validProposal()},${validProposal(id = "other-id")}]}"""

        val result = parser.parse(response, allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
        assertEquals(1, (result as GitHubProposalParseResult.Success).proposals.size)
    }

    @Test
    fun differentProposalsWithSameModelIdReceiveUniqueInternalIds() {
        val response = """
            {
              "proposals":[
                ${validProposal(title = "Erster Vorschlag", id = "model-id")},
                ${validProposal(title = "Zweiter Vorschlag", id = "model-id")}
              ]
            }
        """.trimIndent()

        val result = parser.parse(response, allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
        val ids = (result as GitHubProposalParseResult.Success).proposals.map { it.id }
        assertEquals(2, ids.distinct().size)
        assertTrue(ids.all(SAFE_INTERNAL_ID::matches))
        assertTrue(ids.none { it == "model-id" })
    }

    @Test
    fun internallyGeneratedIdsAreDeterministicForIdenticalResponses() {
        val response = validResponse()

        val first = parser.parse(response, allowedPaths)
        val second = parser.parse(response, allowedPaths)

        assertTrue(first is GitHubProposalParseResult.Success)
        assertTrue(second is GitHubProposalParseResult.Success)
        assertEquals(
            (first as GitHubProposalParseResult.Success).proposals.map { it.id },
            (second as GitHubProposalParseResult.Success).proposals.map { it.id }
        )
        assertTrue(first.proposals.all { SAFE_INTERNAL_ID.matches(it.id) })
    }

    @Test
    fun proposalListsAreBounded() {
        val values = (1..50).joinToString(",") { "\"Änderung $it\"" }
        val response = validResponse().replace(
            "\"suggestedChanges\":[\"Grenze klar dokumentieren.\"]",
            "\"suggestedChanges\":[$values]"
        )

        val result = parser.parse(response, allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
        assertEquals(
            GitHubImprovementProposalParser.MAX_ITEMS_PER_LIST,
            (result as GitHubProposalParseResult.Success)
                .proposals
                .single()
                .suggestedChanges
                .size
        )
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val response = validResponse().replace(
            "\"title\":\"Architekturgrenze schärfen\"",
            "\"title\":\"Architekturgrenze schärfen\",\"futureField\":{\"value\":true}"
        )

        val result = parser.parse(response, allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
    }

    @Test
    fun inventedEvidencePathsAreRejected() {
        val response = validResponse().replace("README.md", "invented/File.kt")

        val result = parser.parse(response, allowedPaths)

        assertEquals(
            GitHubProposalParseResult.Failure(GitHubProposalParseIssue.UNKNOWN_EVIDENCE_PATH),
            result
        )
    }

    @Test
    fun oneUnknownPathRejectsTheWholeMixedResponse() {
        val response = """
            {"proposals":[
              ${validProposal(title = "Valid")},
              ${validProposal(title = "Invalid").replace("README.md", "invented/File.kt")}
            ]}
        """.trimIndent()

        assertEquals(
            GitHubProposalParseResult.Failure(GitHubProposalParseIssue.UNKNOWN_EVIDENCE_PATH),
            parser.parse(response, allowedPaths)
        )
    }

    @Test
    fun urlsAndCredentialLikeValuesAreRemovedFromDisplayText() {
        val response = validResponse()
            .replace(
                "Kopplung reduzieren.",
                "Kopplung reduzieren. https://internal.invalid/path Authorization: Bearer synthetic-token-value"
            )

        val result = parser.parse(response, allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
        val text = (result as GitHubProposalParseResult.Success).proposals.single().summary
        assertFalse(text.contains("https://"))
        assertFalse(text.contains("Authorization", ignoreCase = true))
        assertFalse(text.contains("Bearer", ignoreCase = true))
        assertTrue(text.contains("[Link entfernt]"))
    }

    @Test
    fun everyStructuredDisplayFieldRedactsCredentialClasses() {
        val githubPat = "github_pat_" + "A".repeat(24)
        val assignedToken = "B".repeat(32)
        val privateKeyBody = "C".repeat(80)
        val skKey = "sk-" + "D".repeat(24)
        val googleKey = "AIza" + "E".repeat(28)
        val bearer = "F".repeat(32)
        val response = validResponse()
            .replace("Architekturgrenze schärfen", "Titel $githubPat")
            .replace("Kopplung reduzieren.", "token=$assignedToken")
            .replace(
                "Die Modulgrenze ist dokumentiert.",
                "-----BEGIN PRIVATE KEY-----\\n$privateKeyBody\\n-----END PRIVATE KEY-----"
            )
            .replace("Grenze klar dokumentieren.", skKey)
            .replace("Dokumentationsprüfung ergänzen.", googleKey)
            .replace(
                "Keine Laufzeittests ausgeführt.",
                "Bearer $bearer data:text/plain;base64,${"G".repeat(32)} https://internal.invalid"
            )

        val result = parser.parse(response, allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
        val proposal = (result as GitHubProposalParseResult.Success).proposals.single()
        val displayText = buildList {
            add(proposal.title)
            add(proposal.summary)
            addAll(proposal.evidence.map { it.observation })
            addAll(proposal.suggestedChanges)
            addAll(proposal.testPlan)
            addAll(proposal.limitations)
        }.joinToString("\n")
        listOf(
            githubPat,
            assignedToken,
            privateKeyBody,
            skKey,
            googleKey,
            bearer,
            "BEGIN PRIVATE KEY",
            "data:text",
            "https://"
        ).forEach { sensitive ->
            assertFalse(sensitive, displayText.contains(sensitive, ignoreCase = true))
        }
        assertTrue(displayText.contains("[Zugangsdaten entfernt]"))
    }

    @Test
    fun normalSecurityDocumentationWordsRemainReadable() {
        val response = validResponse().replace(
            "Kopplung reduzieren.",
            "Die Dokumentation erklärt token, secret und authorization ohne Zugangsdaten."
        )

        val result = parser.parse(response, allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
        assertEquals(
            "Die Dokumentation erklärt token, secret und authorization ohne Zugangsdaten.",
            (result as GitHubProposalParseResult.Success).proposals.single().summary
        )
    }

    @Test
    fun unsafeOrNonExactEvidencePathIsRejected() {
        val response = validResponse().replace("README.md", "README\\n.md")

        assertEquals(
            GitHubProposalParseResult.Failure(GitHubProposalParseIssue.UNKNOWN_EVIDENCE_PATH),
            parser.parse(response, allowedPaths)
        )
    }

    @Test
    fun markdownJsonFenceIsAcceptedWithoutWeakeningSchemaValidation() {
        val fenced = "```json\n${validResponse()}\n```"

        val result = parser.parse(fenced, allowedPaths)

        assertTrue(result is GitHubProposalParseResult.Success)
    }

    private fun validResponse(): String = """{"proposals":[${validProposal()}]}"""

    private fun validProposal(
        title: String = "Architekturgrenze schärfen",
        id: String? = "architecture-boundary"
    ): String {
        val idField = id?.let { "\"id\":\"$it\"," }.orEmpty()
        return """
            {
              $idField
              "title":"$title",
              "summary":"Kopplung reduzieren.",
              "category":"ARCHITECTURE",
              "benefit":"HIGH",
              "risk":"LOW",
              "effort":"SMALL",
              "confidence":"HIGH",
              "evidence":[{"path":"README.md","observation":"Die Modulgrenze ist dokumentiert."}],
              "affectedPaths":["README.md"],
              "suggestedChanges":["Grenze klar dokumentieren."],
              "testPlan":["Dokumentationsprüfung ergänzen."],
              "limitations":["Keine Laufzeittests ausgeführt."]
            }
        """.trimIndent()
    }

    companion object {
        private val SAFE_INTERNAL_ID = Regex("^proposal-[0-9a-f]{64}(?:-[0-9]+)?$")
    }
}

class GitHubJsonPayloadExtractorTest {
    private val extractor = GitHubJsonPayloadExtractor()
    private val objectPayload = """{"proposals":[]}"""

    @Test
    fun directJsonObjectIsExtracted() {
        assertSuccess(objectPayload, objectPayload)
    }

    @Test
    fun directJsonArrayIsExtracted() {
        assertSuccess("[]", "[]")
    }

    @Test
    fun fenceWithoutLanguageIsExtracted() {
        assertSuccess("```\n$objectPayload\n```", objectPayload)
    }

    @Test
    fun fenceWithLanguageAndBlankLinesIsExtracted() {
        assertSuccess("```json\n\n$objectPayload\n\n```", objectPayload)
    }

    @Test
    fun shortIntroductionBeforeOnePayloadIsAccepted() {
        assertSuccess("Hier ist das Ergebnis:\n$objectPayload", objectPayload)
    }

    @Test
    fun shortSuffixAfterOnePayloadIsAccepted() {
        assertSuccess("$objectPayload\nEnde der Analyse.", objectPayload)
    }

    @Test
    fun shortTextBeforeAndAfterOnePayloadIsAccepted() {
        assertSuccess("Ergebnis:\n$objectPayload\nEnde.", objectPayload)
    }

    @Test
    fun balancedNonJsonBracketTextBeforePayloadIsIgnored() {
        assertSuccess("Hinweis [nur zur Information]\n$objectPayload", objectPayload)
    }

    @Test
    fun balancedNonJsonBracketTextAfterPayloadIsIgnored() {
        assertSuccess("$objectPayload\nHinweis [nur zur Information]", objectPayload)
    }

    @Test
    fun proseWithQuotedBracketsDoesNotCreateFalseAmbiguity() {
        assertSuccess(
            "Der Satz \"nennt {Klammern} und [Listen]\".\n$objectPayload",
            objectPayload
        )
    }

    @Test
    fun multipleJsonObjectsAreRejectedAsAmbiguous() {
        assertEquals(
            GitHubJsonPayloadExtractionResult.AmbiguousJsonPayload,
            extractor.extract("$objectPayload\n$objectPayload")
        )
    }

    @Test
    fun separateObjectAndArrayAreRejectedAsAmbiguous() {
        assertEquals(
            GitHubJsonPayloadExtractionResult.AmbiguousJsonPayload,
            extractor.extract("$objectPayload\n[]")
        )
    }

    @Test
    fun unbalancedJsonIsRejected() {
        assertEquals(
            GitHubJsonPayloadExtractionResult.InvalidJson,
            extractor.extract("""{"proposals":[]""")
        )
    }

    @Test
    fun truncatedJsonIsRejected() {
        assertEquals(
            GitHubJsonPayloadExtractionResult.InvalidJson,
            extractor.extract("""{"proposals":[{"title":"cut"}""")
        )
    }

    @Test
    fun responseOverTwoHundredFiftySixKibIsRejected() {
        val oversized = "x".repeat(GitHubJsonPayloadExtractor.MAX_RAW_RESPONSE_BYTES) + objectPayload

        assertEquals(
            GitHubJsonPayloadExtractionResult.ResponseTooLarge,
            extractor.extract(oversized)
        )
    }

    @Test
    fun bracketsInsideStringsDoNotBreakScanner() {
        val payload =
            """{"text":"object { value } and array [value]","proposals":[]}"""

        assertSuccess(payload, payload)
    }

    @Test
    fun escapedQuotesDoNotBreakScanner() {
        val payload =
            """{"text":"escaped \"quote\" with } and ]","proposals":[]}"""

        assertSuccess(payload, payload)
    }

    @Test
    fun deeplyNestedArraysAndObjectsRemainCorrect() {
        val payload = """{"metadata":[{"nested":[{"value":"safe"}]}],"proposals":[]}"""

        assertSuccess(payload, payload)
    }

    @Test
    fun manyUnclosedOpeningBracketsAreRejectedInOneForwardPass() {
        val raw = "[".repeat(GitHubJsonPayloadExtractor.MAX_RAW_RESPONSE_BYTES)

        assertEquals(
            GitHubJsonPayloadExtractionResult.InvalidJson,
            extractor.extract(raw)
        )
    }

    @Test
    fun emptyFenceIsRejected() {
        assertEquals(
            GitHubJsonPayloadExtractionResult.InvalidJson,
            extractor.extract("```json\n\n```")
        )
    }

    @Test
    fun freeTextWithoutJsonIsRejected() {
        assertEquals(
            GitHubJsonPayloadExtractionResult.InvalidJson,
            extractor.extract("Hier sind nur freie Hinweise.")
        )
    }

    @Test
    fun lenientTrailingCommaJsonIsRejected() {
        assertEquals(
            GitHubJsonPayloadExtractionResult.InvalidJson,
            extractor.extract("""{"proposals":[],}""")
        )
    }

    private fun assertSuccess(raw: String, expectedPayload: String) {
        assertEquals(
            GitHubJsonPayloadExtractionResult.Success(expectedPayload),
            extractor.extract(raw)
        )
    }
}
