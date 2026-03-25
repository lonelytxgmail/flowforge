package com.flowforge.engine

import com.flowforge.common.model.AppException
import com.flowforge.common.model.ExecutionEventType
import com.flowforge.common.model.NodeType
import com.flowforge.common.model.WorkflowInstanceStatus
import com.flowforge.runtime.infra.ExecutionEventRepository
import com.flowforge.runtime.infra.NodeExecutionRepository
import com.flowforge.runtime.infra.WorkflowInstanceRepository
import com.flowforge.workflow.application.WorkflowDefinitionService
import com.flowforge.workflow.domain.WorkflowDsl
import com.flowforge.workflow.domain.WorkflowNodeDsl
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class StartWorkflowCommand(
    val workflowDefinitionId: Long,
    val inputPayload: Map<String, Any?>? = null
)

/**
 * 这里是第一阶段最核心的“编排器”。
 *
 * 当前先做同步执行，目的是把运行模型走通。
 * 后续你可以把这里拆成：
 * 1. 创建任务
 * 2. worker 拉取任务
 * 3. 执行器异步运行
 */
@Service
class WorkflowEngineService(
    private val workflowDefinitionService: WorkflowDefinitionService,
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val nodeExecutionRepository: NodeExecutionRepository,
    private val executionEventRepository: ExecutionEventRepository
) {

    fun startWorkflow(command: StartWorkflowCommand): Long {
        val version = workflowDefinitionService.getLatestPublishedVersion(command.workflowDefinitionId)

        val instanceId = workflowInstanceRepository.create(
            workflowDefinitionId = version.workflowDefinitionId,
            workflowVersionId = version.id,
            inputPayload = command.inputPayload
        )

        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = null,
            eventType = ExecutionEventType.INSTANCE_CREATED,
            eventMessage = "Workflow instance created"
        )

        try {
            runSynchronously(instanceId, version.dsl, command.inputPayload ?: emptyMap())
            return instanceId
        } catch (ex: Exception) {
            workflowInstanceRepository.updateStatus(
                instanceId = instanceId,
                status = WorkflowInstanceStatus.FAILED,
                currentNodeId = null,
                endedAt = LocalDateTime.now()
            )
            executionEventRepository.append(
                workflowInstanceId = instanceId,
                nodeExecutionId = null,
                eventType = ExecutionEventType.INSTANCE_FAILED,
                eventMessage = "Workflow instance failed",
                eventDetail = mapOf("error" to (ex.message ?: "unknown error"))
            )
            throw ex
        }
    }

    private fun runSynchronously(
        instanceId: Long,
        dsl: WorkflowDsl,
        inputPayload: Map<String, Any?>
    ) {
        val nodesById = dsl.nodes.associateBy { it.id }
        val startNode = dsl.nodes.firstOrNull { it.type == NodeType.START }
            ?: throw AppException("Start node not found")

        workflowInstanceRepository.updateStatus(instanceId, WorkflowInstanceStatus.RUNNING, startNode.id)
        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = null,
            eventType = ExecutionEventType.INSTANCE_STARTED,
            eventMessage = "Workflow instance started"
        )

        var currentNode = startNode
        var currentInput = inputPayload

        while (true) {
            val nodeExecutionId = nodeExecutionRepository.create(
                workflowInstanceId = instanceId,
                nodeId = currentNode.id,
                nodeName = currentNode.name,
                nodeType = currentNode.type.name,
                input = currentInput
            )

            executionEventRepository.append(
                workflowInstanceId = instanceId,
                nodeExecutionId = nodeExecutionId,
                eventType = ExecutionEventType.NODE_STARTED,
                eventMessage = "Node started: ${currentNode.name}"
            )

            val output = executeNode(currentNode, currentInput)

            nodeExecutionRepository.markSucceeded(nodeExecutionId, output)
            executionEventRepository.append(
                workflowInstanceId = instanceId,
                nodeExecutionId = nodeExecutionId,
                eventType = ExecutionEventType.NODE_SUCCEEDED,
                eventMessage = "Node succeeded: ${currentNode.name}",
                eventDetail = output
            )

            if (currentNode.type == NodeType.END) {
                workflowInstanceRepository.updateStatus(
                    instanceId = instanceId,
                    status = WorkflowInstanceStatus.SUCCEEDED,
                    currentNodeId = currentNode.id,
                    endedAt = LocalDateTime.now()
                )
                executionEventRepository.append(
                    workflowInstanceId = instanceId,
                    nodeExecutionId = nodeExecutionId,
                    eventType = ExecutionEventType.INSTANCE_FINISHED,
                    eventMessage = "Workflow instance finished"
                )
                return
            }

            val nextNode = findNextNode(currentNode.id, dsl, nodesById)
            workflowInstanceRepository.updateStatus(instanceId, WorkflowInstanceStatus.RUNNING, nextNode.id)
            currentNode = nextNode
            currentInput = output
        }
    }

    /**
     * 第一阶段先支持三类节点：
     * START / ATOMIC_ABILITY / END
     *
     * 未来如果扩展数字员工、LLM、Agent，这里会演进为 NodeExecutorRegistry。
     */
    private fun executeNode(node: WorkflowNodeDsl, input: Map<String, Any?>): Map<String, Any?> =
        when (node.type) {
            NodeType.START -> input
            NodeType.ATOMIC_ABILITY -> {
                // 这里先模拟一个“原子能力”执行。
                // 后续可以把 config 里的 abilityCode 映射到真实执行器。
                mapOf(
                    "handledBy" to "mock-atomic-ability",
                    "nodeId" to node.id,
                    "message" to "Atomic ability executed successfully",
                    "previousInput" to input
                )
            }
            NodeType.END -> input
            else -> throw AppException("Node type is not implemented in phase 1: ${node.type}")
        }

    private fun findNextNode(
        currentNodeId: String,
        dsl: WorkflowDsl,
        nodesById: Map<String, WorkflowNodeDsl>
    ): WorkflowNodeDsl {
        val edge = dsl.edges.firstOrNull { it.from == currentNodeId }
            ?: throw AppException("Next edge not found for node: $currentNodeId")

        return nodesById[edge.to]
            ?: throw AppException("Next node not found: ${edge.to}")
    }
}

