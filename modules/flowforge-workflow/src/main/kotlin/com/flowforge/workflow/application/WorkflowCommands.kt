package com.flowforge.workflow.application

import com.flowforge.workflow.domain.WorkflowDsl
import jakarta.validation.constraints.NotBlank
import com.flowforge.common.model.NodeType

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

data class SaveNodeTemplateCommand(
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    val description: String?,
    val groupName: String?,
    val nodeType: NodeType,
    val nodeConfig: Map<String, Any?> = emptyMap()
)

data class UpdateNodeTemplateCommand(
    val id: Long,
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    val description: String?,
    val groupName: String?,
    val nodeType: NodeType,
    val nodeConfig: Map<String, Any?> = emptyMap()
)
