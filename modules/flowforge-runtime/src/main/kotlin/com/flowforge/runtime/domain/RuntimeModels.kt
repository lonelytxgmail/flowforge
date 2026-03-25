package com.flowforge.runtime.domain

import com.flowforge.common.model.ExecutionEventType
import com.flowforge.common.model.NodeExecutionStatus
import com.flowforge.common.model.WorkflowInstanceStatus
import java.time.LocalDateTime

data class WorkflowInstance(
    val id: Long,
    val workflowDefinitionId: Long,
    val workflowVersionId: Long,
    val status: WorkflowInstanceStatus,
    val inputPayload: String?,
    val contextJson: String?,
    val currentNodeId: String?,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val createdAt: LocalDateTime
)

data class NodeExecution(
    val id: Long,
    val workflowInstanceId: Long,
    val nodeId: String,
    val nodeName: String,
    val nodeType: String,
    val status: NodeExecutionStatus,
    val attemptNo: Int,
    val inputJson: String?,
    val outputJson: String?,
    val errorMessage: String?,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?
)

data class ExecutionEvent(
    val id: Long,
    val workflowInstanceId: Long,
    val nodeExecutionId: Long?,
    val eventType: ExecutionEventType,
    val eventMessage: String,
    val eventDetail: String?,
    val createdAt: LocalDateTime
)

