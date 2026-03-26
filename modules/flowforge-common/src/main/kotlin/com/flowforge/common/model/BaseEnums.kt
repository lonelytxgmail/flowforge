package com.flowforge.common.model

/**
 * Kotlin 的 enum class 可以理解为“固定值集合”。
 * 这里把系统里常见的状态集中定义，便于后续复用和约束。
 */
enum class WorkflowDefinitionStatus {
    DRAFT,
    ACTIVE,
    DISABLED
}

enum class WorkflowVersionStatus {
    DRAFT,
    PUBLISHED
}

enum class WorkflowInstanceStatus {
    CREATED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    PAUSED
}

enum class TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

enum class NodeExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    WAITING
}

enum class NodeType {
    START,
    END,
    ATOMIC_ABILITY,
    DIGITAL_EMPLOYEE,
    CONDITION,
    WAIT_FOR_FEEDBACK,
    LLM,
    AGENT,
    TOOL
}

enum class ExecutionEventType {
    INSTANCE_CREATED,
    INSTANCE_STARTED,
    INSTANCE_FINISHED,
    INSTANCE_FAILED,
    INSTANCE_PAUSED,
    INSTANCE_RESUMED,
    INSTANCE_RETRIED,
    NODE_WAITING,
    FEEDBACK_RECORDED,
    NODE_STARTED,
    NODE_SUCCEEDED,
    NODE_FAILED
}
