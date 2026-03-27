package com.flowforge.engine

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.flowforge.common.model.AppException
import com.flowforge.common.model.ExecutionEventType
import com.flowforge.common.model.NodeType
import com.flowforge.common.model.WorkflowInstanceStatus
import com.flowforge.runtime.application.WorkflowTaskProcessingGateway
import com.flowforge.runtime.domain.WorkflowTask
import com.flowforge.runtime.infra.ExecutionEventRepository
import com.flowforge.runtime.infra.NodeExecutionRepository
import com.flowforge.runtime.infra.WorkflowInstanceRepository
import com.flowforge.runtime.infra.WorkflowTaskCreateOptions
import com.flowforge.runtime.infra.WorkflowTaskRepository
import com.flowforge.workflow.infra.WorkflowVersionRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 这个 worker 负责“消费 workflow_task 表中的待执行任务”。
 *
 * 这是第一阶段替代 MQ 的核心思路：
 * - 数据库存任务
 * - 应用内 worker 拉任务
 * - 所有状态都可查、可重试、可审计
 */
@Component
class WorkflowTaskWorker(
    private val workflowTaskRepository: WorkflowTaskRepository,
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val workflowVersionRepository: WorkflowVersionRepository,
    private val nodeExecutionRepository: NodeExecutionRepository,
    private val executionEventRepository: ExecutionEventRepository,
    private val nodeExecutorRegistry: NodeExecutorRegistry,
    private val workflowRoutingService: WorkflowRoutingService,
    private val objectMapper: ObjectMapper,
    private val runtimeVariableResolver: RuntimeVariableResolver
) : WorkflowTaskProcessingGateway {
    private val lockOwner: String = "local-worker"

    /**
     * 应用内定时轮询。
     *
     * 这不是“定时触发业务流程”的功能，
     * 而是“定时扫描待执行任务”的 worker 心跳。
     */
    @Scheduled(fixedDelay = 3000L)
    fun pollAndProcessTasks() {
        processAvailableTasks(maxTasks = 20)
    }

    override fun processAvailableTasks(maxTasks: Int) {
        repeat(maxTasks) {
            val task = workflowTaskRepository.claimNextAvailableTask(lockOwner) ?: return
            processTask(task)
        }
    }

    @Transactional
    fun processTask(task: WorkflowTask) {
        val instance = workflowInstanceRepository.findById(task.workflowInstanceId)
            ?: throw AppException("Workflow instance not found: ${task.workflowInstanceId}")

        // 如果实例已经结束了，说明这个任务已经没有必要继续执行。
        if (instance.status == WorkflowInstanceStatus.SUCCEEDED || instance.status == WorkflowInstanceStatus.FAILED) {
            workflowTaskRepository.markCancelled(task.id)
            executionEventRepository.append(
                workflowInstanceId = instance.id,
                nodeExecutionId = null,
                eventType = ExecutionEventType.TASK_CANCELLED,
                eventMessage = "Workflow task cancelled because instance already ended",
                eventDetail = mapOf("taskId" to task.id, "nodeId" to task.nodeId, "instanceStatus" to instance.status.name)
            )
            return
        }

        val version = workflowVersionRepository.findById(instance.workflowVersionId)
            ?: throw AppException("Workflow version not found: ${instance.workflowVersionId}")

        val nodesById = version.dsl.nodes.associateBy { it.id }
        val node = nodesById[task.nodeId]
            ?: throw AppException("Node not found in workflow DSL: ${task.nodeId}")

        val taskInput = parseJsonMap(task.inputJson)
        val workflowContext = parseJsonMap(instance.contextJson)
        val executionScope = mapOf(
            "input" to taskInput,
            "context" to workflowContext,
            "steps" to ((workflowContext["steps"] as? Map<*, *>) ?: emptyMap<String, Any?>()),
            "system" to mapOf(
                "instanceId" to instance.id,
                "workflowDefinitionId" to instance.workflowDefinitionId,
                "workflowVersionId" to instance.workflowVersionId,
                "currentNodeId" to node.id
            )
        )
        val resolvedNode = node.copy(
            config = runtimeVariableResolver.resolveMap(node.config, executionScope)
        )

        // 第一个真正执行的任务到来时，把实例推进到 RUNNING。
        if (instance.status == WorkflowInstanceStatus.CREATED) {
            val updated = workflowInstanceRepository.updateStatusIfCurrent(
                instanceId = instance.id,
                currentStatuses = listOf(WorkflowInstanceStatus.CREATED),
                status = WorkflowInstanceStatus.RUNNING,
                currentNodeId = node.id
            )
            if (!updated) {
                throw AppException("Workflow instance state changed before start: ${instance.id}")
            }
            executionEventRepository.append(
                workflowInstanceId = instance.id,
                nodeExecutionId = null,
                eventType = ExecutionEventType.INSTANCE_STARTED,
                eventMessage = "Workflow instance started",
                eventDetail = mapOf("taskId" to task.id, "nodeId" to node.id)
            )
        }

        executionEventRepository.append(
            workflowInstanceId = instance.id,
            nodeExecutionId = null,
            eventType = ExecutionEventType.TASK_CLAIMED,
            eventMessage = "Workflow task claimed",
            eventDetail = mapOf(
                "taskId" to task.id,
                "nodeId" to task.nodeId,
                "attemptNo" to task.attemptNo,
                "lockOwner" to task.lockOwner
            )
        )

        val nodeExecutionId = nodeExecutionRepository.create(
            workflowInstanceId = instance.id,
            nodeId = node.id,
            nodeName = node.name,
            nodeType = node.type.name,
            input = taskInput
        )

        executionEventRepository.append(
            workflowInstanceId = instance.id,
            nodeExecutionId = nodeExecutionId,
            eventType = ExecutionEventType.NODE_STARTED,
            eventMessage = "Node started: ${node.name}"
        )

        try {
            val executor = nodeExecutorRegistry.getExecutor(node.type)
            val result = executor.execute(
                NodeExecutionContext(
                    workflowInstanceId = instance.id,
                    node = resolvedNode,
                    input = taskInput,
                    workflowContext = workflowContext
                )
            )

            val steps = ((workflowContext["steps"] as? Map<*, *>) ?: emptyMap<String, Any?>())
                .mapKeys { it.key.toString() }
                .toMutableMap()
            steps[node.id] = mapOf(
                "output" to result.output,
                "nodeType" to node.type.name,
                "finishedAt" to LocalDateTime.now().toString()
            )

            val mergedContext = workflowContext +
                result.contextUpdates +
                mapOf("steps" to steps)
            if (result.contextUpdates.isNotEmpty()) {
                workflowInstanceRepository.updateContext(instance.id, mergedContext)
            } else {
                workflowInstanceRepository.updateContext(instance.id, mergedContext)
            }

            workflowTaskRepository.markSucceeded(task.id)

            if (result.outcome == NodeExecutionOutcome.WAITING) {
                nodeExecutionRepository.markWaiting(nodeExecutionId, result.output)
                workflowInstanceRepository.updateStatus(
                    instanceId = instance.id,
                    status = WorkflowInstanceStatus.PAUSED,
                    currentNodeId = node.id
                )
                executionEventRepository.append(
                    workflowInstanceId = instance.id,
                    nodeExecutionId = nodeExecutionId,
                    eventType = ExecutionEventType.NODE_WAITING,
                    eventMessage = "Node is waiting for feedback: ${node.name}",
                    eventDetail = mapOf(
                        "taskId" to task.id,
                        "nodeId" to node.id,
                        "output" to result.output
                    )
                )
                executionEventRepository.append(
                    workflowInstanceId = instance.id,
                    nodeExecutionId = nodeExecutionId,
                    eventType = ExecutionEventType.INSTANCE_PAUSED,
                    eventMessage = "Workflow instance paused for feedback",
                    eventDetail = mapOf("taskId" to task.id, "nodeId" to node.id)
                )
                return
            }

            nodeExecutionRepository.markSucceeded(nodeExecutionId, result.output)
            executionEventRepository.append(
                workflowInstanceId = instance.id,
                nodeExecutionId = nodeExecutionId,
                eventType = ExecutionEventType.NODE_SUCCEEDED,
                eventMessage = "Node succeeded: ${node.name}",
                eventDetail = mapOf(
                    "taskId" to task.id,
                    "nodeId" to node.id,
                    "output" to result.output
                )
            )

            if (node.type == NodeType.END) {
                workflowInstanceRepository.updateStatus(
                    instanceId = instance.id,
                    status = WorkflowInstanceStatus.SUCCEEDED,
                    currentNodeId = node.id,
                    endedAt = LocalDateTime.now()
                )
                executionEventRepository.append(
                    workflowInstanceId = instance.id,
                    nodeExecutionId = nodeExecutionId,
                    eventType = ExecutionEventType.INSTANCE_FINISHED,
                    eventMessage = "Workflow instance finished",
                    eventDetail = mapOf("taskId" to task.id, "nodeId" to node.id)
                )
                return
            }

            val nextNode = workflowRoutingService.resolveNextNodeId(
                dsl = version.dsl,
                currentNode = node,
                output = result.output
            )

            val nextNodeDsl = nodesById[nextNode]
                ?: throw AppException("Next node not found in workflow DSL: $nextNode")

            val nextTaskId = workflowTaskRepository.create(
                workflowInstanceId = instance.id,
                nodeId = nextNodeDsl.id,
                input = result.output,
                options = WorkflowTaskCreateOptions(maxAttempts = readMaxAttempts(nextNodeDsl.config))
            )
            workflowInstanceRepository.updateStatus(
                instanceId = instance.id,
                status = WorkflowInstanceStatus.RUNNING,
                currentNodeId = nextNodeDsl.id
            )
            executionEventRepository.append(
                workflowInstanceId = instance.id,
                nodeExecutionId = nodeExecutionId,
                eventType = ExecutionEventType.TASK_CREATED,
                eventMessage = "Workflow task created",
                eventDetail = mapOf(
                    "taskId" to nextTaskId,
                    "nodeId" to nextNodeDsl.id,
                    "attemptNo" to 1
                )
            )
        } catch (ex: Exception) {
            nodeExecutionRepository.markFailed(nodeExecutionId, ex.message ?: "unknown error")
            workflowTaskRepository.markFailed(task.id, ex.message ?: "unknown error")
            workflowInstanceRepository.updateStatus(
                instanceId = instance.id,
                status = WorkflowInstanceStatus.FAILED,
                currentNodeId = node.id,
                endedAt = LocalDateTime.now()
            )
            executionEventRepository.append(
                workflowInstanceId = instance.id,
                nodeExecutionId = nodeExecutionId,
                eventType = ExecutionEventType.NODE_FAILED,
                eventMessage = "Node failed: ${node.name}",
                eventDetail = mapOf(
                    "taskId" to task.id,
                    "nodeId" to node.id,
                    "attemptNo" to task.attemptNo,
                    "error" to (ex.message ?: "unknown error")
                )
            )
            executionEventRepository.append(
                workflowInstanceId = instance.id,
                nodeExecutionId = nodeExecutionId,
                eventType = ExecutionEventType.INSTANCE_FAILED,
                eventMessage = "Workflow instance failed",
                eventDetail = mapOf(
                    "taskId" to task.id,
                    "nodeId" to node.id,
                    "error" to (ex.message ?: "unknown error")
                )
            )
            throw ex
        }
    }

    private fun readMaxAttempts(config: Map<String, Any?>): Int =
        (config["maxAttempts"] as? Number)?.toInt()?.takeIf { it > 0 } ?: 1

    private fun parseJsonMap(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
    }
}
