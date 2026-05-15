package com.example.bamachat.util

data class McpWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<McpWorkflowStep>,
    val inputSchema: Map<String, Any> = emptyMap()
)

data class McpWorkflowStep(
    val toolName: String,
    val description: String,
    val inputMapping: Map<String, String> = emptyMap(),
    val outputKey: String? = null,
    val condition: String? = null
)

data class McpWorkflowExecution(
    val workflowId: String,
    val runId: String,
    val status: McpWorkflowStatus,
    val stepResults: List<McpWorkflowStepResult> = emptyList(),
    val finalOutput: String? = null,
    val error: String? = null
)

enum class McpWorkflowStatus {
    PENDING, RUNNING, STEP_COMPLETED, COMPLETED, FAILED, CANCELLED
}

data class McpWorkflowStepResult(
    val stepIndex: Int,
    val toolName: String,
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val durationMs: Long = 0
)
