package com.flowforge.api.workflow

import com.flowforge.engine.StartWorkflowCommand
import com.flowforge.engine.WorkflowEngineService
import com.flowforge.workflow.application.CreateWorkflowCommand
import com.flowforge.workflow.application.PublishWorkflowVersionCommand
import com.flowforge.workflow.application.WorkflowDefinitionService
import com.flowforge.workflow.domain.WorkflowDsl
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreateWorkflowRequest(
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    val description: String?
)

data class PublishWorkflowVersionRequest(
    val dsl: WorkflowDsl
)

data class StartWorkflowRequest(
    val inputPayload: Map<String, Any?>? = null
)

@Validated
@RestController
@RequestMapping("/api/workflows")
class WorkflowController(
    private val workflowDefinitionService: WorkflowDefinitionService,
    private val workflowEngineService: WorkflowEngineService
) {

    @Operation(summary = "创建工作流定义")
    @PostMapping
    fun createWorkflow(@Valid @RequestBody request: CreateWorkflowRequest) =
        workflowDefinitionService.createWorkflow(
            CreateWorkflowCommand(
                code = request.code,
                name = request.name,
                description = request.description
            )
        )

    @Operation(summary = "发布工作流版本")
    @PostMapping("/{workflowDefinitionId}/versions")
    fun publishVersion(
        @PathVariable workflowDefinitionId: Long,
        @RequestBody request: PublishWorkflowVersionRequest
    ) = workflowDefinitionService.publishVersion(
        PublishWorkflowVersionCommand(
            workflowDefinitionId = workflowDefinitionId,
            dsl = request.dsl
        )
    )

    @Operation(summary = "启动工作流实例")
    @PostMapping("/{workflowDefinitionId}/instances")
    fun startWorkflow(
        @PathVariable workflowDefinitionId: Long,
        @RequestBody(required = false) request: StartWorkflowRequest?
    ): Map<String, Any> {
        val instanceId = workflowEngineService.startWorkflow(
            StartWorkflowCommand(
                workflowDefinitionId = workflowDefinitionId,
                inputPayload = request?.inputPayload
            )
        )

        return mapOf("instanceId" to instanceId)
    }
}

