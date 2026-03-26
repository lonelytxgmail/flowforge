package com.flowforge.runtime.application

import com.flowforge.common.model.AppException
import com.flowforge.runtime.domain.ExecutionEvent
import com.flowforge.runtime.domain.FeedbackRecord
import com.flowforge.runtime.domain.NodeExecution
import com.flowforge.runtime.domain.WorkflowInstance
import com.flowforge.runtime.infra.ExecutionEventRepository
import com.flowforge.runtime.infra.FeedbackRecordRepository
import com.flowforge.runtime.infra.NodeExecutionRepository
import com.flowforge.runtime.infra.WorkflowInstanceRepository
import org.springframework.stereotype.Service

@Service
class RuntimeQueryService(
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val nodeExecutionRepository: NodeExecutionRepository,
    private val executionEventRepository: ExecutionEventRepository,
    private val feedbackRecordRepository: FeedbackRecordRepository
) {

    fun getInstance(instanceId: Long): WorkflowInstance =
        workflowInstanceRepository.findById(instanceId)
            ?: throw AppException("Workflow instance not found: $instanceId")

    fun getNodeExecutions(instanceId: Long): List<NodeExecution> =
        nodeExecutionRepository.findByWorkflowInstanceId(instanceId)

    fun getExecutionEvents(instanceId: Long): List<ExecutionEvent> =
        executionEventRepository.findByWorkflowInstanceId(instanceId)

    fun getFeedbackRecords(instanceId: Long): List<FeedbackRecord> =
        feedbackRecordRepository.findByWorkflowInstanceId(instanceId)

    fun listInstances(): List<WorkflowInstance> =
        workflowInstanceRepository.findAll()
}
