package com.flowforge.workflow.application

import com.flowforge.common.model.AppException
import com.flowforge.common.model.NodeType
import com.flowforge.workflow.domain.WorkflowDefinition
import com.flowforge.workflow.domain.NodeTemplate
import com.flowforge.workflow.domain.WorkflowVersion
import com.flowforge.workflow.infra.WorkflowDefinitionRepository
import com.flowforge.workflow.infra.NodeTemplateRepository
import com.flowforge.workflow.infra.WorkflowVersionRepository
import org.springframework.stereotype.Service

@Service
class WorkflowDefinitionService(
    private val workflowDefinitionRepository: WorkflowDefinitionRepository,
    private val workflowVersionRepository: WorkflowVersionRepository,
    private val nodeTemplateRepository: NodeTemplateRepository
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

    fun getWorkflowDefinition(id: Long): WorkflowDefinition =
        workflowDefinitionRepository.findById(id)
            ?: throw AppException("Workflow definition not found: $id")

    fun listWorkflowDefinitions(): List<WorkflowDefinition> =
        workflowDefinitionRepository.findAll()

    fun listWorkflowVersions(workflowDefinitionId: Long): List<WorkflowVersion> =
        workflowVersionRepository.findByWorkflowDefinitionId(workflowDefinitionId)

    fun saveNodeTemplate(command: SaveNodeTemplateCommand): NodeTemplate {
        validateNodeTemplate(command)

        val id = nodeTemplateRepository.save(
            code = command.code,
            name = command.name,
            description = command.description,
            nodeType = command.nodeType,
            nodeConfig = command.nodeConfig
        )

        return nodeTemplateRepository.findById(id)
            ?: throw AppException("Node template was created but cannot be loaded: $id")
    }

    fun listNodeTemplates(): List<NodeTemplate> =
        nodeTemplateRepository.findAll()

    fun getNodeTemplate(id: Long): NodeTemplate =
        nodeTemplateRepository.findById(id)
            ?: throw AppException("Node template not found: $id")

    fun updateNodeTemplate(command: UpdateNodeTemplateCommand): NodeTemplate {
        validateNodeTemplate(
            SaveNodeTemplateCommand(
                code = command.code,
                name = command.name,
                description = command.description,
                nodeType = command.nodeType,
                nodeConfig = command.nodeConfig
            )
        )

        val existing = nodeTemplateRepository.findById(command.id)
            ?: throw AppException("Node template not found: ${command.id}")

        nodeTemplateRepository.update(
            id = existing.id,
            code = command.code,
            name = command.name,
            description = command.description,
            nodeType = command.nodeType,
            nodeConfig = command.nodeConfig
        )

        return nodeTemplateRepository.findById(existing.id)
            ?: throw AppException("Node template was updated but cannot be loaded: ${existing.id}")
    }

    fun deleteNodeTemplate(id: Long) {
        val existing = nodeTemplateRepository.findById(id)
            ?: throw AppException("Node template not found: $id")
        nodeTemplateRepository.delete(existing.id)
    }

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

    private fun validateNodeTemplate(command: SaveNodeTemplateCommand) {
        when (command.nodeType) {
            NodeType.START, NodeType.END -> {
                if (command.nodeConfig.isNotEmpty()) {
                    return
                }
            }
            NodeType.ATOMIC_ABILITY -> {
                val abilityType = command.nodeConfig["abilityType"]?.toString()
                val hasMockAbility = command.nodeConfig["abilityCode"] != null
                if (abilityType == null && !hasMockAbility) {
                    throw AppException("ATOMIC_ABILITY template requires config.abilityType or config.abilityCode")
                }
            }
            NodeType.DIGITAL_EMPLOYEE, NodeType.CONDITION, NodeType.WAIT_FOR_FEEDBACK,
            NodeType.LLM, NodeType.AGENT, NodeType.TOOL -> return
        }
    }
}
