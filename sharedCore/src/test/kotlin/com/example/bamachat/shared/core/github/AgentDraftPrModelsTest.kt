package com.example.bamachat.shared.core.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDraftPrModelsTest {
    @Test
    fun deterministicPlanUsesOnlyProposalPathsAndTypedValidationSteps() {
        val first = createPlan()
        val second = createPlan()

        assertEquals(first, second)
        assertEquals(listOf("README.md"), first.affectedPaths)
        assertEquals(
            listOf(AgentValidationId.DIFF_CHECK),
            first.validationSteps
        )
        assertTrue(AgentDraftPrBranchPolicy.isAllowed(first.branchName))
        assertEquals(
            AgentDraftPrPlanValidation.Valid,
            AgentDraftPrPlanPolicy.validate(first, setOf("README.md"), NOW)
        )
    }

    @Test
    fun planAndParserProposalIdentifiersUseSeparateExactFormats() {
        assertTrue(
            AgentDraftPrIdentifierPolicy.isPlanIdAllowed("plan-1234567890abcdef1234")
        )
        listOf(
            PROPOSAL_ID,
            "$PROPOSAL_ID-2",
            "$PROPOSAL_ID-${GitHubReadLimits.MAX_PROPOSALS}"
        ).forEach { id ->
            assertTrue(id, AgentDraftPrIdentifierPolicy.isProposalIdAllowed(id))
        }
        listOf(
            "proposal-${"a".repeat(63)}",
            "proposal-${"a".repeat(65)}",
            "proposal-${"A".repeat(64)}",
            "safe-boundary",
            "proposal-${"a".repeat(64)}\n",
            "$PROPOSAL_ID-1",
            "$PROPOSAL_ID-02",
            "$PROPOSAL_ID-x",
            "$PROPOSAL_ID-${GitHubReadLimits.MAX_PROPOSALS + 1}"
        ).forEach { id ->
            assertFalse(id, AgentDraftPrIdentifierPolicy.isProposalIdAllowed(id))
        }
        listOf(
            "plan-1234567890abcdef123",
            "plan-1234567890abcdef12345",
            "plan-1234567890ABCDEF1234",
            "proposal-${"a".repeat(64)}"
        ).forEach { id ->
            assertFalse(id, AgentDraftPrIdentifierPolicy.isPlanIdAllowed(id))
        }
    }

    @Test
    fun unknownAndProtectedPathsAreRejected() {
        val plan = createPlan()

        assertEquals(
            AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.PATH_NOT_IN_SNAPSHOT),
            AgentDraftPrPlanPolicy.validate(plan, setOf("DEVELOPER_GUIDE.md"), NOW)
        )
        val protectedPlan = plan.copy(
            evidencePaths = listOf(".github/workflows/release.yml"),
            affectedPaths = listOf(".github/workflows/release.yml")
        )
        assertEquals(
            AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.PROTECTED_PATH),
            AgentDraftPrPlanPolicy.validate(
                protectedPlan,
                setOf(".github/workflows/release.yml"),
                NOW
            )
        )
    }

    @Test
    fun moreThanTwelvePathsAreRejected() {
        val paths = (1..13).map { "docs/file-$it.md" }
        val plan = createPlan().copy(affectedPaths = paths)

        assertEquals(
            AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.TOO_MANY_AFFECTED_PATHS),
            AgentDraftPrPlanPolicy.validate(plan, paths.toSet(), NOW)
        )
    }

    @Test
    fun unsafeCommandsAreRejectedInsteadOfExecuted() {
        val plan = createPlan().copy(changeSteps = listOf("git push origin main"))

        assertEquals(
            AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP),
            AgentDraftPrPlanPolicy.validate(plan, setOf("README.md"), NOW)
        )
    }

    @Test
    fun oversizedDeclarativePlanIsRejectedInsteadOfSilentlyTruncated() {
        val proposal = proposal().copy(
            suggestedChanges = (1..13).map { "Deklarativen Schritt $it ergänzen" }
        )

        assertEquals(
            AgentImplementationPlanResult.Failure(AgentDraftPrPlanIssue.TOO_MANY_CHANGE_STEPS),
            AgentImplementationPlanFactory.create(
                proposal = proposal,
                repository = GitHubRepositoryPolicy.repository,
                baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
                baseCommitSha = SHA,
                availablePaths = setOf("README.md"),
                nowEpochSeconds = NOW
            )
        )
    }

    @Test
    fun invalidShaAndUnsafeBranchAreRejected() {
        val plan = createPlan()

        assertEquals(
            AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_BASE_SHA),
            AgentDraftPrPlanPolicy.validate(
                plan.copy(baseCommitSha = "main"),
                setOf("README.md"),
                NOW
            )
        )
        assertEquals(
            AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_BRANCH),
            AgentDraftPrPlanPolicy.validate(
                plan.copy(branchName = "main"),
                setOf("README.md"),
                NOW
            )
        )
    }

    @Test
    fun branchPolicyRejectsMainMasterAndUnsafeRefs() {
        listOf(
            "main",
            "master",
            "bamachat-agent/main-change",
            "bamachat-agent/release-prod",
            "bamachat-agent/../escape",
            "bamachat-agent/Uppercase"
        ).forEach { branch ->
            assertFalse(branch, AgentDraftPrBranchPolicy.isAllowed(branch))
        }
    }

    @Test
    fun generatedBranchesAlwaysSatisfyPolicyForLongAsciiAndUnicodeTitles() {
        listOf(
            "a".repeat(500),
            "Änderung für Sicherheit und Überprüfung ".repeat(30)
        ).forEach { title ->
            val branch = AgentDraftPrBranchPolicy.create(
                "plan-1234567890abcdef1234",
                title
            )

            assertTrue(branch, AgentDraftPrBranchPolicy.isAllowed(branch))
            assertTrue(branch.length <= AgentDraftPrLimits.MAX_BRANCH_CHARS)
            assertTrue(branch.startsWith("bamachat-agent/12345678-"))
        }
    }

    @Test
    fun affectedPathsMustBeCoveredByCanonicalEvidencePaths() {
        val unsupported = proposal().copy(
            evidence = listOf(GitHubProposalEvidence("README.md", "Belegt")),
            affectedPaths = listOf("app/src/main/java/example.kt")
        )
        assertEquals(
            AgentImplementationPlanResult.Failure(
                AgentDraftPrPlanIssue.AFFECTED_PATH_WITHOUT_EVIDENCE
            ),
            AgentImplementationPlanFactory.create(
                proposal = unsupported,
                repository = GitHubRepositoryPolicy.repository,
                baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
                baseCommitSha = SHA,
                availablePaths = setOf("README.md", "app/src/main/java/example.kt"),
                nowEpochSeconds = NOW
            )
        )

        val supported = unsupported.copy(
            evidence = listOf(
                GitHubProposalEvidence("README.md", "Zusätzlicher Beleg"),
                GitHubProposalEvidence("app/src/main/java/example.kt", "Änderung belegt")
            )
        )
        val result = AgentImplementationPlanFactory.create(
            proposal = supported,
            repository = GitHubRepositoryPolicy.repository,
            baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
            baseCommitSha = SHA,
            availablePaths = setOf("README.md", "app/src/main/java/example.kt"),
            nowEpochSeconds = NOW
        ) as AgentImplementationPlanResult.Success

        assertEquals(
            listOf("README.md", "app/src/main/java/example.kt").sorted(),
            result.plan.evidencePaths
        )
        assertEquals(listOf("app/src/main/java/example.kt"), result.plan.affectedPaths)
    }

    @Test
    fun selectionAndPlanUseDefensiveCanonicalPathCopies() {
        val mutableEvidence = mutableListOf("README.md", "README.md")
        val selection = AgentDraftPrProposalSelectionFactory.create(
            proposalId = PROPOSAL_ID,
            sourceRef = GitHubRepositoryPolicy.DEFAULT_REF,
            sourceCommitSha = SHA,
            evidencePaths = mutableEvidence,
            requestedAt = NOW
        )
        mutableEvidence += "local.properties"

        assertEquals(listOf("README.md"), selection.selectedEvidencePaths)
        assertEquals(listOf("README.md"), createPlan().evidencePaths)
    }

    @Test
    fun declarativeChangeStepPolicyBlocksCommandBypasses() {
        val unsafeSteps = listOf(
            "gradle :app:testDebugUnitTest",
            "gradle.bat :app:assembleDebug",
            "C:\\Tools\\gradle\\bin\\gradle.bat test",
            "mvn test",
            "mvnw test",
            ".\\mvnw.cmd test",
            "cmd/c echo test",
            "cmd.exe/c dir",
            "C:\\Windows\\System32\\cmd.exe/c dir",
            "python3 -c print(1)",
            "/usr/bin/python3 -c print(1)",
            "python3.11 -c print(1)",
            "/usr/bin/python3.12 -c print(1)",
            "py -c print(1)",
            "perl -e print",
            "ruby -e puts",
            "php -r echo",
            "zsh -c echo",
            "fish -c echo",
            "bash.exe -c echo",
            "sh.exe -c echo",
            "curl.exe https://example.invalid",
            "wget.exe https://example.invalid",
            "wsl --exec sh",
            "ssh host.example",
            "scp file host:",
            "sftp host.example",
            "ftp host.example",
            "Bitte [gradle test] ausführen",
            "Bitte [cmd/c dir] ausführen",
            "git status",
            "git diff",
            "git log",
            "Danach:git push origin main",
            "Bitte [git diff] ausführen",
            "Bitte [./gradlew :app:testDebugUnitTest] starten",
            ".\\gradlew.bat :app:testDebugUnitTest",
            "./gradlew :sharedCore:test",
            "Dokumentation ändern && git push origin main",
            "Erste Zeile\nzweite Zeile",
            "Danach git push origin main ausführen",
            "Bitte .\\gradlew.bat :app:testDebugUnitTest starten",
            "Im nächsten Schritt powershell -Command Get-ChildItem verwenden",
            "Dokumentation anpassen und danach adb shell starten"
        ) + quotedEscapedAndExpandedCommands() + directArgvCommands()
        unsafeSteps.forEach { step ->
            assertFalse(step, AgentDraftPrChangeStepPolicy.isAllowed(step))
        }
        safeDeclarativeDescriptions().forEach { step ->
            assertTrue(step, AgentDraftPrChangeStepPolicy.isAllowed(step))
        }
    }

    @Test
    fun quotedEscapedAndExpandedCommandsCannotProduceAValidPlan() {
        quotedEscapedAndExpandedCommands().forEach { unsafeStep ->
            val invalidProposal = proposal().copy(suggestedChanges = listOf(unsafeStep))

            assertEquals(
                unsafeStep,
                AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP,
                AgentDraftPrProposalEligibilityPolicy.validate(
                    invalidProposal,
                    setOf("README.md")
                )
            )
            assertEquals(
                unsafeStep,
                AgentImplementationPlanResult.Failure(AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP),
                AgentImplementationPlanFactory.create(
                    proposal = invalidProposal,
                    repository = GitHubRepositoryPolicy.repository,
                    baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
                    baseCommitSha = SHA,
                    availablePaths = setOf("README.md"),
                    nowEpochSeconds = NOW
                )
            )
        }
    }

    @Test
    fun directArgvCommandsAreRejectedAcrossEveryPlanBoundary() {
        directArgvCommands().forEach { unsafeStep ->
            assertFalse(unsafeStep, AgentDraftPrChangeStepPolicy.isAllowed(unsafeStep))
            val invalidProposal = proposal().copy(suggestedChanges = listOf(unsafeStep))
            assertEquals(
                unsafeStep,
                AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP,
                AgentDraftPrProposalEligibilityPolicy.validate(
                    invalidProposal,
                    setOf("README.md")
                )
            )
            assertEquals(
                unsafeStep,
                AgentImplementationPlanResult.Failure(AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP),
                AgentImplementationPlanFactory.create(
                    proposal = invalidProposal,
                    repository = GitHubRepositoryPolicy.repository,
                    baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
                    baseCommitSha = SHA,
                    availablePaths = setOf("README.md"),
                    nowEpochSeconds = NOW
                )
            )
            val reboundPlan = rebindPlan(
                createPlan().copy(changeSteps = listOf(unsafeStep))
            )
            assertEquals(
                unsafeStep,
                AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP),
                AgentDraftPrPlanPolicy.validate(reboundPlan, setOf("README.md"), NOW)
            )
        }
    }

    @Test
    fun declarativeGrammarRejectsMalformedSentencesOptionsAndCommandCamouflage() {
        listOf(
            "ergänzen",
            "123 456",
            "",
            "...",
            "-c Dokumentation ergänzen",
            "--build Dokumentation aktualisieren",
            "Dokumentation -e ergänzen",
            "Dokumentation --exec anpassen",
            "make test dokumentieren",
            "dash echo ergänzen",
            "busybox ash anpassen",
            "Rscript Datei dokumentieren"
        ).forEach { unsafeStep ->
            assertFalse(unsafeStep, AgentDraftPrChangeStepPolicy.isAllowed(unsafeStep))
        }
    }

    @Test
    fun safeDeclarativeDescriptionsRemainBoundUnchangedInValidPlans() {
        safeDeclarativeDescriptions().forEach { safeStep ->
            assertTrue(safeStep, AgentDraftPrChangeStepPolicy.isAllowed(safeStep))
            val result = AgentImplementationPlanFactory.create(
                proposal = proposal().copy(suggestedChanges = listOf(safeStep)),
                repository = GitHubRepositoryPolicy.repository,
                baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
                baseCommitSha = SHA,
                availablePaths = setOf("README.md"),
                nowEpochSeconds = NOW
            )
            assertTrue(safeStep, result is AgentImplementationPlanResult.Success)
            val plan = (result as AgentImplementationPlanResult.Success).plan
            assertEquals(safeStep, listOf(safeStep), plan.changeSteps)
            assertEquals(
                safeStep,
                AgentDraftPrPlanValidation.Valid,
                AgentDraftPrPlanPolicy.validate(plan, setOf("README.md"), NOW)
            )
        }
    }

    @Test
    fun changeStepPromptContractTracksPolicyVerbsAndGuaranteedExamples() {
        val prompt = AgentDraftPrChangeStepPromptContract.promptText
        val verbs = AgentDraftPrChangeStepPromptContract.terminalVerbsForTesting()
        val examples = AgentDraftPrChangeStepPromptContract.compliantExamplesForTesting()

        assertTrue(
            prompt.contains(
                "Beende den Satz exakt mit einem dieser deklarativen Verben: " +
                    verbs.joinToString(", ") + "."
            )
        )
        verbs.forEach { verb ->
            assertTrue(verb, AgentDraftPrChangeStepPolicy.isAllowed("Die Dokumentation $verb"))
        }
        examples.forEach { example ->
            assertTrue(example, prompt.contains(example))
            assertTrue(example, AgentDraftPrChangeStepPolicy.isAllowed(example))
        }
        assertTrue(prompt.contains("keinen Imperativ"))
        assertTrue(prompt.contains("sollte ... werden"))
        assertTrue(prompt.contains("keine CLI-Optionen"))
        assertTrue(prompt.contains("getrennte Arrayelemente"))
    }

    @Test
    fun promptContractDoesNotMakeNonDeclarativeModelTextEligible() {
        listOf(
            "Füge eine Nullprüfung hinzu",
            "Die Fehlerbehandlung sollte verbessert werden",
            "Verwende eine robustere Validierung",
            "Der Test muss ergänzt werden"
        ).forEach { unsafeStep ->
            assertFalse(unsafeStep, AgentDraftPrChangeStepPolicy.isAllowed(unsafeStep))
            assertEquals(
                unsafeStep,
                AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP,
                AgentDraftPrProposalEligibilityPolicy.validate(
                    proposal().copy(suggestedChanges = listOf(unsafeStep)),
                    setOf("README.md")
                )
            )
        }
    }

    @Test
    fun canonicalPlanIdentityBindsFieldAndListBoundariesWithoutCollisions() {
        val original = createPlan()
        val packedLimitations = original.copy(limitations = listOf("a\u001fb"))
        val splitLimitations = original.copy(limitations = listOf("a", "b"))
        val shiftedTitleBoundary = original.copy(
            title = "Titel\u001eZusammenfassung",
            summary = "Ende"
        )
        val shiftedSummaryBoundary = original.copy(
            title = "Titel",
            summary = "Zusammenfassung\u001eEnde"
        )

        assertNotEquals(
            AgentDraftPrPlanIdentity.compute(packedLimitations),
            AgentDraftPrPlanIdentity.compute(splitLimitations)
        )
        assertNotEquals(
            AgentDraftPrPlanIdentity.compute(shiftedTitleBoundary),
            AgentDraftPrPlanIdentity.compute(shiftedSummaryBoundary)
        )
        assertEquals(
            AgentDraftPrPlanIdentity.compute(original),
            AgentDraftPrPlanIdentity.compute(original)
        )
    }

    @Test
    fun proposalAndPlanPoliciesRejectUnsafeHumanReadableText() {
        val unsafeValues = listOf(
            "Text\u001eGrenze",
            "Text\u001fGrenze",
            "Text\u0085Grenze",
            "Text\u2028Grenze",
            "Text\u2029Grenze",
            "Text\u200eGrenze",
            "Text\tGrenze",
            "Text\rGrenze",
            "Text\nGrenze"
        )

        unsafeValues.forEach { unsafe ->
            listOf(
                proposal().copy(title = unsafe),
                proposal().copy(summary = unsafe),
                proposal().copy(limitations = listOf(unsafe))
            ).forEach { invalidProposal ->
                assertEquals(
                    AgentDraftPrPlanIssue.INVALID_LIMITS,
                    AgentDraftPrProposalEligibilityPolicy.validate(
                        invalidProposal,
                        setOf("README.md")
                    )
                )
            }

            listOf(
                createPlan().copy(title = unsafe),
                createPlan().copy(summary = unsafe),
                createPlan().copy(limitations = listOf(unsafe))
            ).forEach { invalidPlan ->
                assertEquals(
                    AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.INVALID_LIMITS),
                    AgentDraftPrPlanPolicy.validate(invalidPlan, setOf("README.md"), NOW)
                )
            }
        }

        listOf(
            "Sichere deutsche Zusammenfassung mit Umlauten",
            "Technischer Bezeichner AgentDraftPrPlanIdentity",
            "Dateipfad sharedCore/src/main/kotlin/Beispiel.kt"
        ).forEach { text -> assertTrue(text, AgentDraftPrTextPolicy.isAllowedSingleLine(text)) }
    }

    @Test
    fun planTimeOrderingIsValidatedAgainstExplicitCurrentTime() {
        val plan = createPlan()
        assertEquals(
            AgentDraftPrPlanValidation.Valid,
            AgentDraftPrPlanPolicy.validate(plan, setOf("README.md"), NOW)
        )
        listOf(
            plan.copy(createdAt = NOW + 1, expiresAt = NOW + 2),
            plan.copy(createdAt = NOW, expiresAt = NOW - 1),
            plan.copy(createdAt = NOW, expiresAt = NOW),
            plan.copy(createdAt = NOW - 1, expiresAt = NOW)
        ).forEach { invalid ->
            assertEquals(
                AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.EXPIRED),
                AgentDraftPrPlanPolicy.validate(invalid, setOf("README.md"), NOW)
            )
        }
        assertEquals(
            AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.EXPIRED),
            AgentDraftPrPlanPolicy.validate(plan, setOf("README.md"), 0)
        )
    }

    @Test
    fun evidencePathLimitIsEnforcedInCorePolicy() {
        val twelvePaths = (1..12).map { "docs/evidence-$it.md" }.sorted()
        val twelveEvidencePlan = rebindPlan(createPlan().copy(
            evidencePaths = twelvePaths,
            affectedPaths = listOf(twelvePaths.first())
        ))
        assertEquals(
            AgentDraftPrPlanValidation.Valid,
            AgentDraftPrPlanPolicy.validate(twelveEvidencePlan, twelvePaths.toSet(), NOW)
        )

        val thirteenPaths = (1..13).map { "docs/evidence-$it.md" }.sorted()
        val thirteenEvidencePlan = createPlan().copy(
            evidencePaths = thirteenPaths,
            affectedPaths = listOf(thirteenPaths.first())
        )
        assertEquals(
            AgentDraftPrPlanValidation.Invalid(
                AgentDraftPrPlanIssue.TOO_MANY_EVIDENCE_PATHS
            ),
            AgentDraftPrPlanPolicy.validate(thirteenEvidencePlan, thirteenPaths.toSet(), NOW)
        )
    }

    @Test
    fun proposalEligibilityPolicyMatchesFactoryForEveryPlanBoundary() {
        val thirteenPaths = (1..13).map { "docs/evidence-$it.md" }.sorted()
        val twelvePaths = thirteenPaths.take(12)
        val availablePaths = thirteenPaths.toSet() + "README.md"
        val invalidProposals = listOf(
            proposal().copy(
                evidence = thirteenPaths.map { GitHubProposalEvidence(it, "Beleg") },
                affectedPaths = listOf(thirteenPaths.first())
            ) to AgentDraftPrPlanIssue.TOO_MANY_EVIDENCE_PATHS,
            proposal().copy(
                evidence = thirteenPaths.map { GitHubProposalEvidence(it, "Beleg") },
                affectedPaths = thirteenPaths
            ) to AgentDraftPrPlanIssue.TOO_MANY_AFFECTED_PATHS,
            proposal().copy(
                evidence = twelvePaths.map { GitHubProposalEvidence(it, "Beleg") },
                affectedPaths = listOf(twelvePaths.first()),
                suggestedChanges = (1..13).map { "Deklarativen Schritt $it ergänzen" }
            ) to AgentDraftPrPlanIssue.TOO_MANY_CHANGE_STEPS,
            proposal().copy(evidence = emptyList()) to
                AgentDraftPrPlanIssue.NO_EVIDENCE_PATHS,
            proposal().copy(affectedPaths = emptyList()) to
                AgentDraftPrPlanIssue.NO_AFFECTED_PATHS,
            proposal().copy(suggestedChanges = emptyList()) to
                AgentDraftPrPlanIssue.EMPTY_CHANGE_STEPS,
            proposal().copy(limitations = List(9) { "Einschränkung $it" }) to
                AgentDraftPrPlanIssue.INVALID_LIMITS,
            proposal().copy(limitations = listOf("")) to
                AgentDraftPrPlanIssue.INVALID_LIMITS,
            proposal().copy(title = "") to AgentDraftPrPlanIssue.INVALID_LIMITS,
            proposal().copy(title = "x".repeat(AgentDraftPrLimits.MAX_TEXT_CHARS + 1)) to
                AgentDraftPrPlanIssue.INVALID_LIMITS,
            proposal().copy(summary = "") to AgentDraftPrPlanIssue.INVALID_LIMITS,
            proposal().copy(summary = "x".repeat(AgentDraftPrLimits.MAX_TEXT_CHARS + 1)) to
                AgentDraftPrPlanIssue.INVALID_LIMITS,
            proposal().copy(id = "safe-boundary") to
                AgentDraftPrPlanIssue.INVALID_PROPOSAL_ID
        )

        invalidProposals.forEach { (invalidProposal, issue) ->
            assertEquals(
                issue,
                AgentDraftPrProposalEligibilityPolicy.validate(invalidProposal, availablePaths)
            )
            assertEquals(
                AgentImplementationPlanResult.Failure(issue),
                AgentImplementationPlanFactory.create(
                    proposal = invalidProposal,
                    repository = GitHubRepositoryPolicy.repository,
                    baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
                    baseCommitSha = SHA,
                    availablePaths = availablePaths,
                    nowEpochSeconds = NOW
                )
            )
        }

        listOf(
            "Danach git push origin main ausführen",
            "Bitte .\\gradlew.bat :app:testDebugUnitTest starten",
            "Dokumentation anpassen und danach adb shell starten"
        ).forEach { unsafeStep ->
            val invalidProposal = proposal().copy(suggestedChanges = listOf(unsafeStep))
            assertEquals(
                AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP,
                AgentDraftPrProposalEligibilityPolicy.validate(invalidProposal, availablePaths)
            )
            assertEquals(
                AgentImplementationPlanResult.Failure(AgentDraftPrPlanIssue.UNSAFE_CHANGE_STEP),
                AgentImplementationPlanFactory.create(
                    proposal = invalidProposal,
                    repository = GitHubRepositoryPolicy.repository,
                    baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
                    baseCommitSha = SHA,
                    availablePaths = availablePaths,
                    nowEpochSeconds = NOW
                )
            )
        }

        assertEquals(
            null,
            AgentDraftPrProposalEligibilityPolicy.validate(proposal(), availablePaths)
        )
        assertTrue(
            AgentImplementationPlanFactory.create(
                proposal = proposal(),
                repository = GitHubRepositoryPolicy.repository,
                baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
                baseCommitSha = SHA,
                availablePaths = availablePaths,
                nowEpochSeconds = NOW
            ) is AgentImplementationPlanResult.Success
        )
    }

    @Test
    fun draftPullRequestUrlPolicyAllowsOnlyMatchingRepositoryUrl() {
        assertTrue(
            AgentDraftPrUrlPolicy.isAllowed(
                "https://github.com/blackstarr595384-stack/BamaChat/pull/42",
                42
            )
        )
        listOf(
            "http://github.com/blackstarr595384-stack/BamaChat/pull/42",
            "https://example.com/blackstarr595384-stack/BamaChat/pull/42",
            "https://github.com/other/BamaChat/pull/42",
            "https://github.com/blackstarr595384-stack/BamaChat/pull/41",
            "https://github.com/blackstarr595384-stack/BamaChat/pull/42?tab=files",
            "https://github.com/blackstarr595384-stack/BamaChat/pull/42#discussion",
            "https://user@github.com/blackstarr595384-stack/BamaChat/pull/42"
        ).forEach { url -> assertFalse(url, AgentDraftPrUrlPolicy.isAllowed(url, 42)) }
    }

    @Test
    fun requestRequiresExplicitApprovalAndBindsPlanDeterministically() {
        val plan = createPlan()

        assertEquals(
            AgentDraftPrRequestResult.Failure(AgentDraftPrIssue.APPROVAL_REQUIRED),
            AgentDraftPrRequestFactory.create(
                plan = plan,
                allowedPaths = setOf("README.md"),
                explicitApproval = false,
                clientVersion = "1.0",
                nowEpochSeconds = NOW
            )
        )
        val first = approvedRequest(plan)
        val second = approvedRequest(plan)
        assertEquals(first, second)
        assertTrue(first.explicitUserApproval)
        assertTrue(first.idempotencyKey.startsWith("idem-"))
        assertEquals(69, first.idempotencyKey.length)
    }

    @Test
    fun changedPlanProducesDifferentRequestAndIdempotencyKeys() {
        val plan = createPlan()
        val changedPlan = (AgentImplementationPlanFactory.create(
            proposal = proposal().copy(summary = "Geänderte sichere Zusammenfassung"),
            repository = GitHubRepositoryPolicy.repository,
            baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
            baseCommitSha = SHA,
            availablePaths = setOf("README.md"),
            nowEpochSeconds = NOW
        ) as AgentImplementationPlanResult.Success).plan

        val first = approvedRequest(plan)
        val second = approvedRequest(changedPlan)

        assertNotEquals(first.requestId, second.requestId)
        assertNotEquals(first.idempotencyKey, second.idempotencyKey)
    }

    @Test
    fun anyConsentRelevantPlanContentChangesThePlanId() {
        val original = createPlan()
        val changedPlans = listOf(
            createPlanFrom(proposal().copy(id = "proposal-${"f".repeat(64)}")),
            createPlanFrom(proposal().copy(title = "Andere sichere Grenze")),
            createPlanFrom(proposal().copy(summary = "Andere sichere Zusammenfassung")),
            createPlanFrom(proposal().copy(suggestedChanges = listOf("Andere Grenze beschreiben"))),
            createPlanFrom(proposal().copy(risk = GitHubProposalRisk.MEDIUM)),
            createPlanFrom(proposal().copy(limitations = listOf("Andere Einschränkung"))),
            createPlanFrom(
                proposal = proposal(),
                baseRef = GitHubRepositoryPolicy.RELEASED_BRANCH
            ),
            createPlanFrom(proposal = proposal(), baseCommitSha = "a".repeat(40)),
            createPlanFrom(
                proposal = proposal().copy(
                    evidence = listOf(
                        GitHubProposalEvidence("DEVELOPER_GUIDE.md", "Andere Evidenz")
                    ),
                    affectedPaths = listOf("DEVELOPER_GUIDE.md")
                ),
                availablePaths = setOf("DEVELOPER_GUIDE.md")
            ),
            createPlanFrom(
                proposal = proposal().copy(
                    evidence = listOf(
                        GitHubProposalEvidence("app/src/main/java/example.kt", "Android-Evidenz")
                    ),
                    affectedPaths = listOf("app/src/main/java/example.kt")
                ),
                availablePaths = setOf("app/src/main/java/example.kt")
            )
        )

        val originalRequest = approvedRequest(original)
        changedPlans.forEach { changed ->
            val changedRequest = approvedRequest(changed, changed.evidencePaths.toSet())
            assertNotEquals(original.planId, changed.planId)
            assertNotEquals(originalRequest.requestId, changedRequest.requestId)
            assertNotEquals(originalRequest.idempotencyKey, changedRequest.idempotencyKey)
        }
    }

    @Test
    fun stalePlanIdentityRejectsEveryConsentRelevantContentChange() {
        val original = createPlan()
        val availablePaths = setOf("README.md", "DEVELOPER_GUIDE.md")
        val staleIdentityPlans = listOf(
            original.copy(
                changeSteps = listOf("Zusätzliche Dokumentationsgrenze beschreiben")
            ),
            original.copy(limitations = listOf("Andere Einschränkung")),
            original.copy(risk = GitHubProposalRisk.MEDIUM),
            original.copy(evidencePaths = listOf("DEVELOPER_GUIDE.md", "README.md")),
            original.copy(
                evidencePaths = listOf("DEVELOPER_GUIDE.md", "README.md"),
                affectedPaths = listOf("DEVELOPER_GUIDE.md")
            ),
            original.copy(baseRef = GitHubRepositoryPolicy.RELEASED_BRANCH)
        )

        staleIdentityPlans.forEach { tampered ->
            assertEquals(
                AgentDraftPrPlanValidation.Invalid(
                    AgentDraftPrPlanIssue.PLAN_ID_CONTENT_MISMATCH
                ),
                AgentDraftPrPlanPolicy.validate(tampered, availablePaths, NOW)
            )
        }
        assertEquals(
            AgentDraftPrPlanValidation.Invalid(
                AgentDraftPrPlanIssue.INVALID_VALIDATION_STEPS
            ),
            AgentDraftPrPlanPolicy.validate(
                original.copy(
                    validationSteps = listOf(
                        AgentValidationId.DIFF_CHECK,
                        AgentValidationId.ANDROID_UNIT_TEST
                    )
                ),
                availablePaths,
                NOW
            )
        )
        assertEquals(
            AgentDraftPrPlanValidation.Invalid(AgentDraftPrPlanIssue.REPOSITORY_NOT_ALLOWED),
            AgentDraftPrPlanPolicy.validate(
                original.copy(repository = GitHubRepositoryRef("other", "BamaChat")),
                availablePaths,
                NOW
            )
        )
        assertEquals(
            AgentDraftPrRequestResult.Failure(AgentDraftPrIssue.PLAN_INVALID),
            AgentDraftPrRequestFactory.create(
                plan = original.copy(
                    changeSteps = listOf("Zusätzliche Dokumentationsgrenze beschreiben")
                ),
                allowedPaths = availablePaths,
                explicitApproval = true,
                clientVersion = "1.0",
                nowEpochSeconds = NOW
            )
        )
    }

    @Test
    fun clientModelsContainNoGitHubCredentialOrCommandFields() {
        val forbidden = setOf(
            "githubToken",
            "authorizationHeader",
            "installationToken",
            "privateKey",
            "rawModelResponse",
            "repositoryText",
            "shellCommand",
            "gitCommand"
        )
        val names = listOf(
            AgentDraftPrProposalSelection::class.java,
            AgentImplementationPlan::class.java,
            AgentDraftPrRequest::class.java,
            AgentDraftPrResult::class.java
        ).flatMap { type -> type.declaredFields.map { it.name } }

        assertTrue(names.intersect(forbidden).isEmpty())
    }

    @Test
    fun statusTransitionsAreStrictlyMonotonicAndTerminal() {
        assertTrue(
            AgentDraftPrStatusPolicy.canTransition(
                AgentDraftPrStatus.SERVER_ACCEPTED,
                AgentDraftPrStatus.BRANCH_CREATED
            )
        )
        assertFalse(
            AgentDraftPrStatusPolicy.canTransition(
                AgentDraftPrStatus.SERVER_ACCEPTED,
                AgentDraftPrStatus.TESTS_RUNNING
            )
        )
        assertTrue(
            AgentDraftPrStatusPolicy.canTransition(
                AgentDraftPrStatus.TESTS_RUNNING,
                AgentDraftPrStatus.TESTS_FAILED
            )
        )
        assertTrue(
            AgentDraftPrStatusPolicy.canTransition(
                AgentDraftPrStatus.READY_FOR_SERVER_SUBMISSION,
                AgentDraftPrStatus.CANCELLED
            )
        )
        listOf(
            AgentDraftPrStatus.SERVER_ACCEPTED,
            AgentDraftPrStatus.BRANCH_CREATED,
            AgentDraftPrStatus.CHANGES_APPLIED,
            AgentDraftPrStatus.TESTS_RUNNING,
            AgentDraftPrStatus.TESTS_PASSED,
            AgentDraftPrStatus.DRAFT_PR_CREATED,
            AgentDraftPrStatus.TESTS_FAILED,
            AgentDraftPrStatus.FAILED
        ).forEach { status ->
            assertFalse(
                status.name,
                AgentDraftPrStatusPolicy.canTransition(status, AgentDraftPrStatus.CANCELLED)
            )
        }
        assertFalse(
            AgentDraftPrStatusPolicy.canTransition(
                AgentDraftPrStatus.DRAFT_PR_CREATED,
                AgentDraftPrStatus.CANCELLED
            )
        )
    }

    @Test
    fun modulePathsSelectOnlyAllowlistedValidationIds() {
        val proposal = proposal().copy(
            affectedPaths = listOf(
                "app/src/main/java/example.kt",
                "desktopApp/src/main/kotlin/example.kt",
                "sharedCore/src/main/kotlin/example.kt"
            ),
            evidence = listOf(
                GitHubProposalEvidence("app/src/main/java/example.kt", "Android-Beleg"),
                GitHubProposalEvidence("desktopApp/src/main/kotlin/example.kt", "Desktop-Beleg"),
                GitHubProposalEvidence("sharedCore/src/main/kotlin/example.kt", "Core-Beleg")
            )
        )
        val paths = proposal.affectedPaths.toSet()
        val result = AgentImplementationPlanFactory.create(
            proposal = proposal,
            repository = GitHubRepositoryPolicy.repository,
            baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
            baseCommitSha = SHA,
            availablePaths = paths,
            nowEpochSeconds = NOW
        ) as AgentImplementationPlanResult.Success

        assertEquals(
            listOf(
                AgentValidationId.DIFF_CHECK,
                AgentValidationId.SHARED_CORE_TEST,
                AgentValidationId.ANDROID_UNIT_TEST,
                AgentValidationId.ANDROID_COMPILE,
                AgentValidationId.ANDROID_ASSEMBLE,
                AgentValidationId.DESKTOP_COMPILE,
                AgentValidationId.DESKTOP_TEST
            ),
            result.plan.validationSteps
        )
    }

    @Test
    fun validationPolicyRequiresExactCompleteOrderedSteps() {
        val readmePlan = createPlan()
        assertEquals(
            listOf(AgentValidationId.DIFF_CHECK),
            AgentDraftPrValidationPolicy.requiredFor(readmePlan.affectedPaths)
        )
        assertEquals(
            AgentDraftPrPlanValidation.Valid,
            AgentDraftPrPlanPolicy.validate(readmePlan, setOf("README.md"), NOW)
        )

        val appPlan = createPlanForPaths(listOf("app/src/main/java/example.kt"))
        val sharedPlan = createPlanForPaths(listOf("sharedCore/src/main/kotlin/example.kt"))
        val desktopPlan = createPlanForPaths(listOf("desktopApp/src/main/kotlin/example.kt"))
        val invalidPlans = listOf(
            appPlan.copy(
                validationSteps = appPlan.validationSteps.filterNot {
                    it == AgentValidationId.ANDROID_UNIT_TEST
                }
            ),
            appPlan.copy(validationSteps = listOf(AgentValidationId.DIFF_CHECK)),
            sharedPlan.copy(validationSteps = listOf(AgentValidationId.DIFF_CHECK)),
            desktopPlan.copy(validationSteps = listOf(AgentValidationId.DIFF_CHECK)),
            readmePlan.copy(
                validationSteps = listOf(
                    AgentValidationId.DIFF_CHECK,
                    AgentValidationId.DESKTOP_COMPILE
                )
            ),
            appPlan.copy(validationSteps = appPlan.validationSteps.reversed())
        )

        invalidPlans.forEach { invalid ->
            assertEquals(
                AgentDraftPrPlanValidation.Invalid(
                    AgentDraftPrPlanIssue.INVALID_VALIDATION_STEPS
                ),
                AgentDraftPrPlanPolicy.validate(
                    invalid,
                    invalid.evidencePaths.toSet(),
                    NOW
                )
            )
        }
        assertEquals(
            AgentDraftPrRequestResult.Failure(AgentDraftPrIssue.PLAN_INVALID),
            AgentDraftPrRequestFactory.create(
                plan = appPlan.copy(validationSteps = listOf(AgentValidationId.DIFF_CHECK)),
                allowedPaths = appPlan.evidencePaths.toSet(),
                explicitApproval = true,
                clientVersion = "1.0",
                nowEpochSeconds = NOW
            )
        )
    }

    @Test
    fun expiredPlanCannotProduceRequest() {
        val plan = createPlan().copy(expiresAt = NOW)

        assertEquals(
            AgentDraftPrRequestResult.Failure(AgentDraftPrIssue.PLAN_INVALID),
            AgentDraftPrRequestFactory.create(
                plan = plan,
                allowedPaths = setOf("README.md"),
                explicitApproval = true,
                clientVersion = "1.0",
                nowEpochSeconds = NOW
            )
        )
    }

    private fun createPlan(): AgentImplementationPlan {
        return createPlanFrom(proposal())
    }

    private fun createPlanFrom(
        proposal: GitHubImprovementProposal,
        baseRef: String = GitHubRepositoryPolicy.DEFAULT_REF,
        baseCommitSha: String = SHA,
        availablePaths: Set<String> = setOf("README.md")
    ): AgentImplementationPlan {
        val result = AgentImplementationPlanFactory.create(
            proposal = proposal,
            repository = GitHubRepositoryPolicy.repository,
            baseRef = baseRef,
            baseCommitSha = baseCommitSha,
            availablePaths = availablePaths,
            nowEpochSeconds = NOW
        )
        return (result as AgentImplementationPlanResult.Success).plan
    }

    private fun createPlanForPaths(paths: List<String>): AgentImplementationPlan {
        val result = AgentImplementationPlanFactory.create(
            proposal = proposal().copy(
                evidence = paths.map { GitHubProposalEvidence(it, "Beleg") },
                affectedPaths = paths
            ),
            repository = GitHubRepositoryPolicy.repository,
            baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
            baseCommitSha = SHA,
            availablePaths = paths.toSet(),
            nowEpochSeconds = NOW
        )
        return (result as AgentImplementationPlanResult.Success).plan
    }

    private fun rebindPlan(plan: AgentImplementationPlan): AgentImplementationPlan {
        val planId = AgentDraftPrPlanIdentity.compute(plan)
        return plan.copy(
            planId = planId,
            branchName = AgentDraftPrBranchPolicy.create(planId, plan.title)
        )
    }

    private fun approvedRequest(
        plan: AgentImplementationPlan,
        allowedPaths: Set<String> = setOf("README.md")
    ): AgentDraftPrRequest {
        val result = AgentDraftPrRequestFactory.create(
            plan = plan,
            allowedPaths = allowedPaths,
            explicitApproval = true,
            clientVersion = "1.0",
            nowEpochSeconds = NOW
        )
        return (result as AgentDraftPrRequestResult.Success).request
    }

    private fun quotedEscapedAndExpandedCommands(): List<String> = listOf(
        "\"git\" status",
        "'gradle' test",
        "g\\it status",
        "\"bash\" -c echo",
        "g\"it\" status",
        "g''it status",
        "ba'sh' -c echo",
        "g^it status",
        "g${'$'}@it status",
        "g${'$'}{EMPTY}it status",
        "%COMSPEC% /c dir",
        "!COMSPEC! /c dir",
        "g?it status",
        "g*it status",
        "g[abc]it status",
        "g{,}it status",
        "./git status",
        "\"/usr/bin/git\" status",
        "'./gradlew' test",
        "\"C:\\Windows\\System32\\cmd.exe\" /c dir",
        "\"scripts/check.ps1\"",
        "'scripts/check.bat'",
        "scripts/check^.cmd",
        "scripts/check\\.sh"
    )

    private fun directArgvCommands(): List<String> = listOf(
        "dash -c echo",
        "ash -c echo",
        "ksh -c echo",
        "csh -c echo",
        "tcsh -c echo",
        "busybox ash -c echo",
        "git-shell -c echo",
        "make test",
        "cmake --build .",
        "ninja test",
        "lua -e print",
        "Rscript -e print",
        "deno eval code",
        "bun run script",
        "dotnet run",
        "groovy script",
        "kotlinc -script file",
        "swift script",
        "nc example.com 443",
        "netcat example.com 443",
        "telnet example.com 23",
        "socat address",
        "http example.com",
        "aria2c example.com",
        "git-status"
    )

    private fun safeDeclarativeDescriptions(): List<String> = listOf(
        "Die Dokumentation um eine sichere Beschreibung ergänzen",
        "Das Push-Verbot in der Dokumentation präzisieren",
        "Die Git-Diff-Prüfung beschreiben",
        "Den Android-Testplan verständlicher dokumentieren",
        "Die Versionskontrollrichtlinie präzisieren",
        "Die Python-Dokumentation präzisieren",
        "Die CMD-Sicherheitsgrenze dokumentieren",
        "Die Gradle-Konfiguration dokumentieren",
        "Die Maven-Abhängigkeiten beschreiben",
        "Die SSH-Sicherheitsgrenze dokumentieren",
        "README.md aktualisieren",
        "AgentDraftPrChangeStepPolicy absichern",
        "Fehlerbehandlung in GitHubIntelligenceViewModel verbessern",
        "Tests für die Planvalidierung ergänzen",
        "die Dokumentation präzisieren",
        "DIE DOKUMENTATION PRÄZISIEREN",
        "Provider-Auswahl absichern",
        "Änderungslogik mit Umlauten präzisieren"
    )

    private fun proposal(): GitHubImprovementProposal = GitHubImprovementProposal(
        id = PROPOSAL_ID,
        title = "Sichere Grenze dokumentieren",
        summary = "Die Sicherheitsgrenze bleibt prüfbar.",
        category = GitHubProposalCategory.SECURITY,
        benefit = GitHubProposalBenefit.HIGH,
        risk = GitHubProposalRisk.LOW,
        effort = GitHubProposalEffort.SMALL,
        confidence = GitHubProposalConfidence.HIGH,
        evidence = listOf(GitHubProposalEvidence("README.md", "Die Grenze ist belegt.")),
        affectedPaths = listOf("README.md"),
        suggestedChanges = listOf("Dokumentation präzisieren"),
        testPlan = listOf("Policy-Test ergänzen"),
        limitations = listOf("Keine Laufzeittests ausgeführt")
    )

    companion object {
        private const val NOW = 1_800_000_000L
        private const val SHA = "9a5c5e58711ad470374e4ab134b61ce8bc8399b8"
        private const val PROPOSAL_ID =
            "proposal-0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
