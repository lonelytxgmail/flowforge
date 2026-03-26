package com.flowforge.runtime.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.flowforge.common.model.AppException
import com.flowforge.common.model.ExecutionEventType
import com.flowforge.common.model.TaskStatus
import com.flowforge.common.model.WorkflowInstanceStatus
import com.flowforge.runtime.infra.ExecutionEventRepository
import com.flowforge.runtime.infra.FeedbackRecordRepository
import com.flowforge.runtime.infra.NodeExecutionRepository
import com.flowforge.runtime.infra.WorkflowInstanceRepository
import com.flowforge.runtime.infra.WorkflowTaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 运行态命令服务：
 * - pause
 * - resume
 * - retry
 *
 * Query 和 Command 分开写，代码会更清晰。
 */
@Service
class RuntimeCommandService(
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val workflowTaskRepository: WorkflowTaskRepository,
    private val executionEventRepository: ExecutionEventRepository,
    private val nodeExecutionRepository: NodeExecutionRepository,
    private val feedbackRecordRepository: FeedbackRecordRepository,
    private val workflowDefinitionRuntimeGateway: WorkflowDefinitionRuntimeGateway,
    private val objectMapper: ObjectMapper,
    private val workflowTaskProcessingGateway: WorkflowTaskProcessingGateway
) {

    @Transactional
    fun pauseInstance(instanceId: Long) {
        val instance = workflowInstanceRepository.findById(instanceId)
            ?: throw AppException("Workflow instance not found: $instanceId")

        if (instance.status != WorkflowInstanceStatus.RUNNING && instance.status != WorkflowInstanceStatus.CREATED) {
            throw AppException("Only RUNNING or CREATED instances can be paused")
        }

        workflowInstanceRepository.updateStatus(
            instanceId = instanceId,
            status = WorkflowInstanceStatus.PAUSED,
            currentNodeId = instance.currentNodeId
        )

        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = null,
            eventType = ExecutionEventType.INSTANCE_PAUSED,
            eventMessage = "Workflow instance paused"
        )
    }

    @Transactional
    fun resumeInstance(instanceId: Long) {
        val instance = workflowInstanceRepository.findById(instanceId)
            ?: throw AppException("Workflow instance not found: $instanceId")

        if (instance.status != WorkflowInstanceStatus.PAUSED) {
            throw AppException("Only PAUSED instances can be resumed")
        }

        workflowInstanceRepository.updateStatus(
            instanceId = instanceId,
            status = WorkflowInstanceStatus.RUNNING,
            currentNodeId = instance.currentNodeId
        )

        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = null,
            eventType = ExecutionEventType.INSTANCE_RESUMED,
            eventMessage = "Workflow instance resumed"
        )
    }

    @Transactional
    fun retryInstance(instanceId: Long) {
        val instance = workflowInstanceRepository.findById(instanceId)
            ?: throw AppException("Workflow instance not found: $instanceId")

        if (instance.status != WorkflowInstanceStatus.FAILED) {
            throw AppException("Only FAILED instances can be retried")
        }

        val failedTask = workflowTaskRepository.findLatestFailedTask(instanceId)
            ?: throw AppException("No failed task found for instance: $instanceId")

        val retryInput = parseJsonMap(failedTask.inputJson)

        workflowTaskRepository.requeueTask(
            workflowInstanceId = instanceId,
            nodeId = failedTask.nodeId,
            input = retryInput
        )

        workflowInstanceRepository.updateStatus(
            instanceId = instanceId,
            status = WorkflowInstanceStatus.RUNNING,
            currentNodeId = failedTask.nodeId,
            endedAt = null
        )

        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = null,
            eventType = ExecutionEventType.INSTANCE_RETRIED,
            eventMessage = "Workflow instance retried",
            eventDetail = mapOf("nodeId" to failedTask.nodeId)
        )
    }

    @Transactional
    fun submitFeedback(
        instanceId: Long,
        feedbackType: String,
        feedbackPayload: Map<String, Any?>?,
        createdBy: String?
    ) {
        val instance = workflowInstanceRepository.findById(instanceId)
            ?: throw AppException("Workflow instance not found: $instanceId")

        if (instance.status != WorkflowInstanceStatus.PAUSED) {
            throw AppException("Only PAUSED instances can receive feedback")
        }

        val waitingNodeExecution = nodeExecutionRepository.findLatestWaitingByWorkflowInstanceId(instanceId)
            ?: throw AppException("No waiting node execution found for instance: $instanceId")

        val currentNodeTypeName = workflowDefinitionRuntimeGateway.getNodeTypeName(
            instanceId = instanceId,
            nodeId = waitingNodeExecution.nodeId
        )

        if (currentNodeTypeName != "WAIT_FOR_FEEDBACK") {
            throw AppException("Current waiting node is not WAIT_FOR_FEEDBACK")
        }

        feedbackRecordRepository.create(
            workflowInstanceId = instanceId,
            nodeExecutionId = waitingNodeExecution.id,
            feedbackType = feedbackType,
            feedbackPayload = feedbackPayload,
            createdBy = createdBy
        )

        val mergedOutput = mergeOutput(waitingNodeExecution.outputJson, feedbackPayload, feedbackType, createdBy)
        nodeExecutionRepository.markSucceeded(waitingNodeExecution.id, mergedOutput)

        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = waitingNodeExecution.id,
            eventType = ExecutionEventType.FEEDBACK_RECORDED,
            eventMessage = "Feedback recorded for waiting node",
            eventDetail = mapOf(
                "feedbackType" to feedbackType,
                "createdBy" to createdBy,
                "feedbackPayload" to feedbackPayload
            )
        )

        val nextNodeId = workflowDefinitionRuntimeGateway.getNextNodeId(
            instanceId = instanceId,
            nodeId = waitingNodeExecution.nodeId
        )

        workflowTaskRepository.requeueTask(
            workflowInstanceId = instanceId,
            nodeId = nextNodeId,
            input = mergedOutput
        )
        workflowInstanceRepository.updateStatus(
            instanceId = instanceId,
            status = WorkflowInstanceStatus.RUNNING,
            currentNodeId = nextNodeId
        )
        executionEventRepository.append(
            workflowInstanceId = instanceId,
            nodeExecutionId = waitingNodeExecution.id,
            eventType = ExecutionEventType.INSTANCE_RESUMED,
            eventMessage = "Workflow instance resumed by feedback"
        )

        workflowTaskProcessingGateway.processAvailableTasks(20)
    }

    private fun parseJsonMap(json: String?): Map<String, Any?> {
        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
    }

    private fun mergeOutput(
        existingOutputJson: String?,
        feedbackPayload: Map<String, Any?>?,
        feedbackType: String,
        createdBy: String?
    ): Map<String, Any?> {
        val base = parseJsonMap(existingOutputJson).toMutableMap()
        base["feedback"] = mapOf(
            "type" to feedbackType,
            "payload" to feedbackPayload,
            "createdBy" to createdBy
        )
        return base
    }
}
