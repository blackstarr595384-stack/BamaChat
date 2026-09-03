package com.example.bamachat.data.github

import com.example.bamachat.shared.core.github.AgentDraftPrGatewayResult
import com.example.bamachat.shared.core.github.AgentDraftPrBranchPolicy
import com.example.bamachat.shared.core.github.AgentDraftPrIssue
import com.example.bamachat.shared.core.github.AgentDraftPrPlanIdentity
import com.example.bamachat.shared.core.github.AgentDraftPrRequest
import com.example.bamachat.shared.core.github.AgentImplementationPlan
import com.example.bamachat.shared.core.github.AgentValidationId
import com.example.bamachat.shared.core.github.GitHubProposalRisk
import com.example.bamachat.shared.core.github.GitHubRepositoryPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DisabledAgentDraftPrGatewayTest {
    private val gateway = DisabledAgentDraftPrGateway { NOW }

    @Test
    fun gatewayIsExplicitlyUnavailableAndPerformsNoSubmission() = runTest {
        assertFalse(gateway.serverAvailable)

        val result = gateway.submitDraftPrRequest(request())

        assertEquals(
            AgentDraftPrGatewayResult.Failure(
                AgentDraftPrIssue.SERVER_NOT_CONNECTED,
                DisabledAgentDraftPrGateway.SERVER_NOT_CONNECTED_MESSAGE
            ),
            result
        )
    }

    @Test
    fun statusAndCancellationRemainDisabledWithoutNetworkFallback() = runTest {
        assertEquals(
            AgentDraftPrIssue.SERVER_NOT_CONNECTED,
            (gateway.getDraftPrStatus("request-safe") as AgentDraftPrGatewayResult.Failure).issue
        )
        assertEquals(
            AgentDraftPrIssue.SERVER_NOT_CONNECTED,
            (gateway.cancelDraftPrRequest("request-safe") as AgentDraftPrGatewayResult.Failure).issue
        )
    }

    @Test
    fun disabledGatewayContainsNoHttpClientEndpointOrCredentialState() {
        val fieldTypes = DisabledAgentDraftPrGateway::class.java.declaredFields
            .map { it.type.name }
            .joinToString(" ")
        val fieldNames = DisabledAgentDraftPrGateway::class.java.declaredFields
            .map { it.name }
            .joinToString(" ")

        assertFalse(fieldTypes.contains("okhttp", ignoreCase = true))
        assertFalse(fieldTypes.contains("retrofit", ignoreCase = true))
        assertFalse(fieldTypes.contains("httpurl", ignoreCase = true))
        assertFalse(fieldNames.contains("token", ignoreCase = true))
        assertFalse(fieldNames.contains("authorization", ignoreCase = true))
        assertFalse(fieldNames.contains("endpoint", ignoreCase = true))
    }

    @Test
    fun expiredPlanIsRejectedUsingInjectedCurrentTime() = runTest {
        val result = gateway.validatePlan(plan().copy(expiresAt = NOW), setOf("README.md"))

        assertEquals(
            AgentDraftPrIssue.PLAN_INVALID,
            (result as AgentDraftPrGatewayResult.Failure).issue
        )
    }

    @Test
    fun futurePlanIsAcceptedAndExpiryBoundaryIsRejected() = runTest {
        val future = plan().copy(expiresAt = NOW + 1)

        assertEquals(
            AgentDraftPrGatewayResult.Success(future),
            gateway.validatePlan(future, setOf("README.md"))
        )
        val boundaryGateway = DisabledAgentDraftPrGateway { NOW + 1 }
        assertEquals(
            AgentDraftPrIssue.PLAN_INVALID,
            (boundaryGateway.validatePlan(future, setOf("README.md")) as
                AgentDraftPrGatewayResult.Failure).issue
        )
    }

    @Test
    fun gatewayRejectsMoreThanTwelveEvidencePaths() = runTest {
        val paths = (1..13).map { "docs/evidence-$it.md" }.sorted()
        val oversized = plan().copy(
            evidencePaths = paths,
            affectedPaths = listOf(paths.first())
        )

        val result = gateway.validatePlan(oversized, paths.toSet())

        assertEquals(
            AgentDraftPrIssue.PLAN_INVALID,
            (result as AgentDraftPrGatewayResult.Failure).issue
        )
    }

    private fun plan(): AgentImplementationPlan {
        val unbound = AgentImplementationPlan(
            planId = "",
            proposalId = PROPOSAL_ID,
            title = "Sichere Grenze dokumentieren",
            summary = "Die Sicherheitsgrenze bleibt prüfbar.",
            repository = GitHubRepositoryPolicy.repository,
            baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
            baseCommitSha = "9a5c5e58711ad470374e4ab134b61ce8bc8399b8",
            branchName = "",
            evidencePaths = listOf("README.md"),
            affectedPaths = listOf("README.md"),
            changeSteps = listOf("Dokumentation präzisieren"),
            validationSteps = listOf(AgentValidationId.DIFF_CHECK),
            risk = GitHubProposalRisk.LOW,
            limitations = listOf("Kein Live-Auftrag"),
            createdAt = NOW - 1,
            expiresAt = NOW + 1
        )
        val planId = AgentDraftPrPlanIdentity.compute(unbound)
        return unbound.copy(
            planId = planId,
            branchName = AgentDraftPrBranchPolicy.create(planId, unbound.title)
        )
    }

    private fun request(): AgentDraftPrRequest = AgentDraftPrRequest(
        requestId = "request-safe",
        idempotencyKey = "idem-safe",
        planId = "plan-1234567890abcdef1234",
        repositoryOwner = GitHubRepositoryPolicy.OWNER,
        repositoryName = GitHubRepositoryPolicy.REPOSITORY,
        baseRef = GitHubRepositoryPolicy.DEFAULT_REF,
        baseCommitSha = "9a5c5e58711ad470374e4ab134b61ce8bc8399b8",
        branchName = "bamachat-agent/12345678-dokumentation-harten",
        approvedPaths = listOf("README.md"),
        approvedChangeSteps = listOf("Dokumentation präzisieren"),
        approvedValidationSteps = listOf(AgentValidationId.DIFF_CHECK),
        explicitUserApproval = true,
        clientVersion = "test"
    )

    companion object {
        private const val NOW = 1_800_000_000L
        private const val PROPOSAL_ID =
            "proposal-0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
