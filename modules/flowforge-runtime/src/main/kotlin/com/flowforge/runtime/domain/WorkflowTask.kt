package com.flowforge.runtime.domain

import com.flowforge.common.model.TaskStatus
import java.time.LocalDateTime

/**
 * 工作流任务是“待执行的一步”。
 *
 * 你可以把它理解为：
 * - WorkflowInstance 表示“一次流程运行”
 * - WorkflowTask 表示“这次运行里，接下来要执行哪个节点”
 */
data class WorkflowTask(
    val id: Long,
    val workflowInstanceId: Long,
    val nodeId: String,
    val status: TaskStatus,
    val attemptNo: Int,
    val sourceTaskId: Long?,
    val inputJson: String?,
    val retryReason: String?,
    val availableAt: LocalDateTime,
    val lockedAt: LocalDateTime?,
    val lockOwner: String?,
    val errorMessage: String?,
    val maxAttempts: Int,
    val timeoutSeconds: Int?,
    val retryBackoffSeconds: Int?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
