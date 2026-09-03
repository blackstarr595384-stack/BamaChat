package com.example.bamachat.data.github

import com.example.bamachat.shared.core.github.AgentDraftPrGateway
import com.example.bamachat.shared.core.github.AgentDraftPrGatewayResult
import com.example.bamachat.shared.core.github.AgentDraftPrIssue
import com.example.bamachat.shared.core.github.AgentDraftPrPlanPolicy
import com.example.bamachat.shared.core.github.AgentDraftPrPlanValidation
import com.example.bamachat.shared.core.github.AgentDraftPrRequest
import com.example.bamachat.shared.core.github.AgentDraftPrResult
import com.example.bamachat.shared.core.github.AgentImplementationPlan

class DisabledAgentDraftPrGateway(
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L }
) : AgentDraftPrGateway {
    override val serverAvailable: Boolean = false

    override suspend fun validatePlan(
        plan: AgentImplementationPlan,
        allowedPaths: Set<String>
    ): AgentDraftPrGatewayResult<AgentImplementationPlan> {
        return when (
            AgentDraftPrPlanPolicy.validate(
                plan,
                allowedPaths,
                currentEpochSeconds()
            )
        ) {
            AgentDraftPrPlanValidation.Valid -> AgentDraftPrGatewayResult.Success(plan)
            is AgentDraftPrPlanValidation.Invalid -> AgentDraftPrGatewayResult.Failure(
                issue = AgentDraftPrIssue.PLAN_INVALID,
                safeMessage = INVALID_PLAN_MESSAGE
            )
        }
    }

    override suspend fun submitDraftPrRequest(
        request: AgentDraftPrRequest
    ): AgentDraftPrGatewayResult<AgentDraftPrResult> = notConnected()

    override suspend fun getDraftPrStatus(
        requestId: String
    ): AgentDraftPrGatewayResult<AgentDraftPrResult> = notConnected()

    override suspend fun cancelDraftPrRequest(
        requestId: String
    ): AgentDraftPrGatewayResult<AgentDraftPrResult> = notConnected()

    private fun notConnected(): AgentDraftPrGatewayResult.Failure {
        return AgentDraftPrGatewayResult.Failure(
            issue = AgentDraftPrIssue.SERVER_NOT_CONNECTED,
            safeMessage = SERVER_NOT_CONNECTED_MESSAGE
        )
    }

    companion object {
        const val SERVER_NOT_CONNECTED_MESSAGE = "Sicherer Agent-Service noch nicht verbunden"
        const val INVALID_PLAN_MESSAGE = "Der Umsetzungsplan erfüllt die Sicherheitsgrenzen nicht."
    }
}
