package com.example.bamachat.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class McpWorkflowManager(private val mcpServerManager: McpServerManager) {

    private val _workflows = MutableStateFlow<List<McpWorkflow>>(emptyList())
    val workflows: StateFlow<List<McpWorkflow>> = _workflows.asStateFlow()

    private val _executions = MutableStateFlow<List<McpWorkflowExecution>>(emptyList())
    val executions: StateFlow<List<McpWorkflowExecution>> = _executions.asStateFlow()

    init {
        registerDefaultWorkflows()
    }

    private fun registerDefaultWorkflows() {
        _workflows.value = listOf(
            McpWorkflow(
                id = "web-research-summary",
                name = "Web-Recherche & Zusammenfassung",
                description = "Durchsucht das Web zu einem Thema und fasst die Ergebnisse zusammen.",
                steps = listOf(
                    McpWorkflowStep(toolName = "web_search", description = "Web-Suche durchführen", outputKey = "search_results"),
                    McpWorkflowStep(toolName = "web_fetch", description = "Top-Ergebnisse abrufen", outputKey = "page_content")
                ),
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Suchanfrage")
                    ),
                    "required" to listOf("query")
                )
            ),
            McpWorkflow(
                id = "code-review-flow",
                name = "Code-Review-Pipeline",
                description = "Analysiert Code in einem Verzeichnis und erstellt eine Review-Zusammenfassung.",
                steps = listOf(
                    McpWorkflowStep(toolName = "read_file", description = "Datei einlesen", outputKey = "file_content"),
                    McpWorkflowStep(toolName = "execute_command", description = "Lint/Test ausführen", outputKey = "lint_output")
                ),
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Dateipfad für das Review")
                    ),
                    "required" to listOf("path")
                )
            )
        )
    }

    fun addWorkflow(workflow: McpWorkflow) {
        _workflows.value = _workflows.value + workflow
    }

    fun removeWorkflow(id: String) {
        _workflows.value = _workflows.value.filter { it.id != id }
    }

    suspend fun executeWorkflow(
        workflowId: String,
        inputs: Map<String, Any> = emptyMap()
    ): McpWorkflowExecution {
        val workflow = _workflows.value.find { it.id == workflowId }
            ?: return McpWorkflowExecution(
                workflowId = workflowId, runId = UUID.randomUUID().toString(),
                status = McpWorkflowStatus.FAILED, error = "Workflow '$workflowId' nicht gefunden"
            )

        val runId = UUID.randomUUID().toString()
        val exec = McpWorkflowExecution(workflowId = workflowId, runId = runId, status = McpWorkflowStatus.RUNNING)
        _executions.value = _executions.value + exec

        val stepResults = mutableListOf<McpWorkflowStepResult>()
        val context = inputs.toMutableMap()

        for ((index, step) in workflow.steps.withIndex()) {
            val startTime = System.currentTimeMillis()
            val resolvedArgs = step.inputMapping.mapValues { (_, value) ->
                if (value.startsWith("$.")) {
                    val key = value.removePrefix("$.")
                    context[key] ?: value
                } else value
            }

            val result = try {
                mcpServerManager.callTool(step.toolName, resolvedArgs)
            } catch (e: Exception) {
                McpToolResult(success = false, content = listOf(McpContentItem(type = "text", text = e.message ?: "Fehler")), isError = true)
            }

            val stepResult = McpWorkflowStepResult(
                stepIndex = index, toolName = step.toolName, success = result.success,
                output = result.content.joinToString("\n") { it.text ?: it.data ?: "" },
                error = if (!result.success) result.content.firstOrNull()?.text else null,
                durationMs = System.currentTimeMillis() - startTime
            )
            stepResults.add(stepResult)
            if (step.outputKey != null && result.success) {
                context[step.outputKey] = stepResult.output ?: ""
            }
            if (!result.success) break
        }

        val finalExec = exec.copy(
            status = if (stepResults.all { it.success }) McpWorkflowStatus.COMPLETED else McpWorkflowStatus.FAILED,
            stepResults = stepResults,
            finalOutput = stepResults.lastOrNull()?.output,
            error = stepResults.firstOrNull { !it.success }?.error
        )
        _executions.value = _executions.value.map { if (it.runId == runId) finalExec else it }
        return finalExec
    }

    fun getOpenAIToolDefinitions(): List<Map<String, Any>> {
        return _workflows.value.map { wf ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "workflow_${wf.id}",
                    "description" to wf.description,
                    "parameters" to wf.inputSchema
                )
            )
        }
    }

    fun clearExecutions() {
        _executions.value = emptyList()
    }
}
