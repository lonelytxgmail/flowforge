package com.flowforge.workflow.application

import com.flowforge.workflow.domain.WorkflowDsl
import jakarta.validation.constraints.NotBlank

data class CreateWorkflowCommand(
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    val description: String?
)

data class PublishWorkflowVersionCommand(
    val workflowDefinitionId: Long,
    val dsl: WorkflowDsl
)

