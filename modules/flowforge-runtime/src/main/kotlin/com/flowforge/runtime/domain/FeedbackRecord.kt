package com.flowforge.runtime.domain

import java.time.LocalDateTime

data class FeedbackRecord(
    val id: Long,
    val workflowInstanceId: Long,
    val nodeExecutionId: Long?,
    val feedbackType: String,
    val feedbackPayload: String?,
    val createdBy: String?,
    val createdAt: LocalDateTime
)
