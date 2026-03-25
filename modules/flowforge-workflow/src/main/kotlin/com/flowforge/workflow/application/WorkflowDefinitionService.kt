package com.flowforge.workflow.application

import com.flowforge.common.model.AppException
import com.flowforge.common.model.NodeType
import com.flowforge.workflow.domain.WorkflowDefinition
import com.flowforge.workflow.domain.WorkflowVersion
import com.flowforge.workflow.infra.WorkflowDefinitionRepository
import com.flowforge.workflow.infra.WorkflowVersionRepository
import org.springframework.stereotype.Service

@Service
class WorkflowDefinitionService(
    private val workflowDefinitionRepository: WorkflowDefinitionRepository,
    private val workflowVersionRepository: WorkflowVersionRepository
) {

    fun createWorkflow(command: CreateWorkflowCommand): WorkflowDefinition {
        val id = workflowDefinitionRepository.save(
            code = command.code,
            name = command.name,
            description = command.description
        )

        return workflowDefinitionRepository.findById(id)
            ?: throw AppException("Workflow definition was created but cannot be loaded: $id")
    }

    fun publishVersion(command: PublishWorkflowVersionCommand): WorkflowVersion {
        validateDsl(command.dsl)

        val definition = workflowDefinitionRepository.findById(command.workflowDefinitionId)
            ?: throw AppException("Workflow definition not found: ${command.workflowDefinitionId}")

        val nextVersion = workflowVersionRepository.nextVersionNo(definition.id)
        val versionId = workflowVersionRepository.savePublishedVersion(definition.id, nextVersion, command.dsl)

        return workflowVersionRepository.findById(versionId)
            ?: throw AppException("Workflow version was created but cannot be loaded: $versionId")
    }

    fun getLatestPublishedVersion(workflowDefinitionId: Long): WorkflowVersion =
        workflowVersionRepository.findLatestPublishedVersion(workflowDefinitionId)
            ?: throw AppException("No published workflow version found for definition: $workflowDefinitionId")

    /**
     * 第一阶段先做最必要的 DSL 校验：
     * 1. 必须有 start / end
     * 2. 节点 id 不能重复
     */
    private fun validateDsl(dsl: com.flowforge.workflow.domain.WorkflowDsl) {
        val nodeIds = dsl.nodes.map { it.id }
        if (nodeIds.distinct().size != nodeIds.size) {
            throw AppException("Node ids must be unique")
        }
        if (dsl.nodes.none { it.type == NodeType.START }) {
            throw AppException("DSL must contain a START node")
        }
        if (dsl.nodes.none { it.type == NodeType.END }) {
            throw AppException("DSL must contain an END node")
        }
    }
}
