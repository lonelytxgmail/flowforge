package com.flowforge.engine

import com.flowforge.common.model.AppException
import com.flowforge.common.model.ExecutionEventType
import com.flowforge.runtime.infra.ExecutionEventRepository
import com.flowforge.runtime.infra.WorkflowInstanceRepository
import com.flowforge.runtime.infra.WorkflowTaskCreateOptions
import com.flowforge.runtime.infra.WorkflowTaskRepository
import com.flowforge.workflow.application.WorkflowDefinitionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class StartWorkflowCommand(
    val workflowDefinitionId: Long,
    val inputPayload: Map<String, Any?>? = null
)

/**
 * 现在这个服务不再直接 while 循环执行所有节点，
 * 而是：
 * 1. 创建实例
 * 2. 写入第一条 workflow_task
 * 3. 交给 worker 处理
 *
 * 这样做以后，暂停 / 恢复 / 重试 / 定时触发都会更容易接入。
 */
@Service
class WorkflowEngineService(
    private val workflowDefinitionService: WorkflowDefinitionService,
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val workflowTaskRepository: WorkflowTaskRepository,
    private val workflowTaskWorker: WorkflowTaskWorker,
    private val executionEventRepository: ExecutionEventRepository
) {
    private val logger = LoggerFactory.getLogger(WorkflowEngineService::class.java)

    fun startWorkflow(command: StartWorkflowCommand): Long {
        val version = workflowDefinitionService.getLatestPublishedVersion(command.workflowDefinitionId)
        val startNode = version.dsl.nodes.firstOrNull { it.type == com.flowforge.common.model.NodeType.START }
            ?: throw AppException("Start node not found")

        val instanceId = workflowInstanceRepository.create(
            workflowDefinitionId = version.workflowDefinitionId,
            workflowVersionId = version.id,
            inputPayload = command.inputPayload
        )

        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = null,
            eventType = ExecutionEventType.INSTANCE_CREATED,
            eventMessage = "Workflow instance created",
            eventDetail = mapOf(
                "workflowDefinitionId" to version.workflowDefinitionId,
                "workflowVersionId" to version.id,
                "startNodeId" to startNode.id
            )
        )

        val taskId = workflowTaskRepository.create(
            workflowInstanceId = instanceId,
            nodeId = startNode.id,
            input = command.inputPayload ?: emptyMap(),
            options = WorkflowTaskCreateOptions(maxAttempts = 1)
        )
        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = null,
            eventType = ExecutionEventType.TASK_CREATED,
            eventMessage = "Workflow task created",
            eventDetail = mapOf(
                "taskId" to taskId,
                "nodeId" to startNode.id,
                "attemptNo" to 1
            )
        )

        // 为了保持第一阶段 API 体验简单，这里启动后会立即尝试消费任务。
        // 将来如果完全异步化，可以去掉这一行，只保留 @Scheduled worker。
        try {
            workflowTaskWorker.processAvailableTasks(maxTasks = version.dsl.nodes.size + 2)
        } catch (ex: Exception) {
            logger.warn("Workflow instance {} started but immediate task processing failed: {}", instanceId, ex.message)
        }

        return instanceId
    }
}
