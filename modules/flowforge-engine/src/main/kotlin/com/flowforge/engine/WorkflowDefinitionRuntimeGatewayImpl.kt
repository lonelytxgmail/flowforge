package com.flowforge.engine

import com.flowforge.common.model.AppException
import com.flowforge.runtime.application.WorkflowDefinitionRuntimeGateway
import com.flowforge.runtime.infra.WorkflowInstanceRepository
import com.flowforge.workflow.infra.WorkflowVersionRepository
import org.springframework.stereotype.Component

/**
 * 这层实现把 runtime 需要的 DSL 信息封装起来，
 * 避免 runtime 直接依赖 workflow 模块实现细节。
 */
@Component
class WorkflowDefinitionRuntimeGatewayImpl(
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val workflowVersionRepository: WorkflowVersionRepository
) : WorkflowDefinitionRuntimeGateway {

    override fun getNodeTypeName(instanceId: Long, nodeId: String): String {
        val version = loadVersion(instanceId)
        return version.dsl.nodes.firstOrNull { it.id == nodeId }?.type?.name
            ?: throw AppException("Node not found in workflow DSL: $nodeId")
    }

    override fun getNextNodeId(instanceId: Long, nodeId: String): String {
        val version = loadVersion(instanceId)
        return version.dsl.edges.firstOrNull { it.from == nodeId }?.to
            ?: throw AppException("Next edge not found for node: $nodeId")
    }

    private fun loadVersion(instanceId: Long) =
        workflowVersionRepository.findById(
            workflowInstanceRepository.findById(instanceId)?.workflowVersionId
                ?: throw AppException("Workflow instance not found: $instanceId")
        ) ?: throw AppException("Workflow version not found for instance: $instanceId")
}
