package com.flowforge.runtime.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowforge.common.json.toJsonb
import com.flowforge.common.model.ExecutionEventType
import com.flowforge.common.model.NodeExecutionStatus
import com.flowforge.common.model.TaskStatus
import com.flowforge.common.model.WorkflowInstanceStatus
import com.flowforge.runtime.domain.ExecutionEvent
import com.flowforge.runtime.domain.FeedbackRecord
import com.flowforge.runtime.domain.NodeExecution
import com.flowforge.runtime.domain.WorkflowInstance
import com.flowforge.runtime.domain.WorkflowTask
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

data class WorkflowTaskCreateOptions(
    val attemptNo: Int = 1,
    val sourceTaskId: Long? = null,
    val retryReason: String? = null,
    val availableAt: LocalDateTime? = null,
    val maxAttempts: Int = 1,
    val timeoutSeconds: Int? = null,
    val retryBackoffSeconds: Int? = null
)

@Repository
class WorkflowInstanceRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper
) {

    fun create(
        workflowDefinitionId: Long,
        workflowVersionId: Long,
        inputPayload: Map<String, Any?>?
    ): Long =
        jdbcClient.sql(
            """
            INSERT INTO workflow_instance(
                workflow_definition_id, workflow_version_id, status, input_payload, context_json, current_node_id, started_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
            """
        )
            .param(workflowDefinitionId)
            .param(workflowVersionId)
            .param(WorkflowInstanceStatus.CREATED.name)
            .param(objectMapper.toJsonb(inputPayload))
            .param(objectMapper.toJsonb(emptyMap<String, Any?>()))
            .param(null)
            .query(Long::class.java)
            .single()

    fun updateStatus(instanceId: Long, status: WorkflowInstanceStatus, currentNodeId: String?, endedAt: LocalDateTime? = null) {
        jdbcClient.sql(
            """
            UPDATE workflow_instance
            SET status = ?, current_node_id = ?, ended_at = ?
            WHERE id = ?
            """
        )
            .param(status.name)
            .param(currentNodeId)
            .param(endedAt)
            .param(instanceId)
            .update()
    }

    fun updateStatusIfCurrent(
        instanceId: Long,
        currentStatuses: Collection<WorkflowInstanceStatus>,
        status: WorkflowInstanceStatus,
        currentNodeId: String?,
        endedAt: LocalDateTime? = null
    ): Boolean {
        if (currentStatuses.isEmpty()) {
            return false
        }

        val placeholders = currentStatuses.joinToString(",") { "?" }
        val sql = """
            UPDATE workflow_instance
            SET status = ?, current_node_id = ?, ended_at = ?
            WHERE id = ? AND status IN ($placeholders)
        """.trimIndent()

        val statement = jdbcClient.sql(sql)
            .param(status.name)
            .param(currentNodeId)
            .param(endedAt)
            .param(instanceId)
        currentStatuses.forEach { statement.param(it.name) }
        return statement.update() > 0
    }

    fun findStatus(instanceId: Long): WorkflowInstanceStatus? =
        jdbcClient.sql(
            """
            SELECT status
            FROM workflow_instance
            WHERE id = ?
            """
        )
            .param(instanceId)
            .query(String::class.java)
            .optional()
            .map { WorkflowInstanceStatus.valueOf(it) }
            .orElse(null)

    fun updateContext(instanceId: Long, context: Map<String, Any?>) {
        jdbcClient.sql(
            """
            UPDATE workflow_instance
            SET context_json = ?
            WHERE id = ?
            """
        )
            .param(objectMapper.toJsonb(context))
            .param(instanceId)
            .update()
    }

    fun findAll(): List<WorkflowInstance> =
        jdbcClient.sql(
            """
            SELECT id, workflow_definition_id, workflow_version_id, status, input_payload, context_json, current_node_id, started_at, ended_at, created_at
            FROM workflow_instance
            ORDER BY id DESC
            """
        )
            .query { rs, _ ->
                WorkflowInstance(
                    id = rs.getLong("id"),
                    workflowDefinitionId = rs.getLong("workflow_definition_id"),
                    workflowVersionId = rs.getLong("workflow_version_id"),
                    status = WorkflowInstanceStatus.valueOf(rs.getString("status")),
                    inputPayload = rs.getString("input_payload"),
                    contextJson = rs.getString("context_json"),
                    currentNodeId = rs.getString("current_node_id"),
                    startedAt = rs.getTimestamp("started_at").toLocalDateTime(),
                    endedAt = rs.getTimestamp("ended_at")?.toLocalDateTime(),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime()
                )
            }
            .list()

    fun findById(id: Long): WorkflowInstance? =
        jdbcClient.sql(
            """
            SELECT id, workflow_definition_id, workflow_version_id, status, input_payload, context_json, current_node_id, started_at, ended_at, created_at
            FROM workflow_instance
            WHERE id = ?
            """
        )
            .param(id)
            .query { rs, _ ->
                WorkflowInstance(
                    id = rs.getLong("id"),
                    workflowDefinitionId = rs.getLong("workflow_definition_id"),
                    workflowVersionId = rs.getLong("workflow_version_id"),
                    status = WorkflowInstanceStatus.valueOf(rs.getString("status")),
                    inputPayload = rs.getString("input_payload"),
                    contextJson = rs.getString("context_json"),
                    currentNodeId = rs.getString("current_node_id"),
                    startedAt = rs.getTimestamp("started_at").toLocalDateTime(),
                    endedAt = rs.getTimestamp("ended_at")?.toLocalDateTime(),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime()
                )
            }
            .optional()
            .orElse(null)
}

@Repository
class NodeExecutionRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper
) {

    fun create(
        workflowInstanceId: Long,
        nodeId: String,
        nodeName: String,
        nodeType: String,
        input: Map<String, Any?>?
    ): Long =
        jdbcClient.sql(
            """
            INSERT INTO node_execution(
                workflow_instance_id, node_id, node_name, node_type, status, attempt_no, input_json, started_at
            ) VALUES (?, ?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP)
            RETURNING id
            """
        )
            .param(workflowInstanceId)
            .param(nodeId)
            .param(nodeName)
            .param(nodeType)
            .param(NodeExecutionStatus.RUNNING.name)
            .param(objectMapper.toJsonb(input))
            .query(Long::class.java)
            .single()

    fun markSucceeded(id: Long, output: Map<String, Any?>?) {
        jdbcClient.sql(
            """
            UPDATE node_execution
            SET status = ?, output_json = ?, ended_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """
        )
            .param(NodeExecutionStatus.SUCCEEDED.name)
            .param(objectMapper.toJsonb(output))
            .param(id)
            .update()
    }

    fun markFailed(id: Long, errorMessage: String) {
        jdbcClient.sql(
            """
            UPDATE node_execution
            SET status = ?, error_message = ?, ended_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """
        )
            .param(NodeExecutionStatus.FAILED.name)
            .param(errorMessage)
            .param(id)
            .update()
    }

    fun markWaiting(id: Long, output: Map<String, Any?>?) {
        jdbcClient.sql(
            """
            UPDATE node_execution
            SET status = ?, output_json = ?, ended_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """
        )
            .param(NodeExecutionStatus.WAITING.name)
            .param(objectMapper.toJsonb(output))
            .param(id)
            .update()
    }

    fun findLatestWaitingByWorkflowInstanceId(workflowInstanceId: Long): NodeExecution? =
        jdbcClient.sql(
            """
            SELECT id, workflow_instance_id, node_id, node_name, node_type, status, attempt_no, input_json, output_json, error_message, started_at, ended_at
            FROM node_execution
            WHERE workflow_instance_id = ? AND status = ?
            ORDER BY id DESC
            LIMIT 1
            """
        )
            .param(workflowInstanceId)
            .param(NodeExecutionStatus.WAITING.name)
            .query { rs, _ ->
                NodeExecution(
                    id = rs.getLong("id"),
                    workflowInstanceId = rs.getLong("workflow_instance_id"),
                    nodeId = rs.getString("node_id"),
                    nodeName = rs.getString("node_name"),
                    nodeType = rs.getString("node_type"),
                    status = NodeExecutionStatus.valueOf(rs.getString("status")),
                    attemptNo = rs.getInt("attempt_no"),
                    inputJson = rs.getString("input_json"),
                    outputJson = rs.getString("output_json"),
                    errorMessage = rs.getString("error_message"),
                    startedAt = rs.getTimestamp("started_at").toLocalDateTime(),
                    endedAt = rs.getTimestamp("ended_at")?.toLocalDateTime()
                )
            }
            .optional()
            .orElse(null)

    fun findByWorkflowInstanceId(workflowInstanceId: Long): List<NodeExecution> =
        jdbcClient.sql(
            """
            SELECT id, workflow_instance_id, node_id, node_name, node_type, status, attempt_no, input_json, output_json, error_message, started_at, ended_at
            FROM node_execution
            WHERE workflow_instance_id = ?
            ORDER BY id
            """
        )
            .param(workflowInstanceId)
            .query { rs, _ ->
                NodeExecution(
                    id = rs.getLong("id"),
                    workflowInstanceId = rs.getLong("workflow_instance_id"),
                    nodeId = rs.getString("node_id"),
                    nodeName = rs.getString("node_name"),
                    nodeType = rs.getString("node_type"),
                    status = NodeExecutionStatus.valueOf(rs.getString("status")),
                    attemptNo = rs.getInt("attempt_no"),
                    inputJson = rs.getString("input_json"),
                    outputJson = rs.getString("output_json"),
                    errorMessage = rs.getString("error_message"),
                    startedAt = rs.getTimestamp("started_at").toLocalDateTime(),
                    endedAt = rs.getTimestamp("ended_at")?.toLocalDateTime()
                )
            }
            .list()
}

@Repository
class ExecutionEventRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper
) {

    fun append(
        workflowInstanceId: Long,
        nodeExecutionId: Long?,
        eventType: ExecutionEventType,
        eventMessage: String,
        eventDetail: Map<String, Any?>? = null
    ) {
        jdbcClient.sql(
            """
            INSERT INTO execution_event(
                workflow_instance_id, node_execution_id, event_type, event_message, event_detail, created_at
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """
        )
            .param(workflowInstanceId)
            .param(nodeExecutionId)
            .param(eventType.name)
            .param(eventMessage)
            .param(objectMapper.toJsonb(eventDetail))
            .update()
    }

    fun findByWorkflowInstanceId(workflowInstanceId: Long): List<ExecutionEvent> =
        jdbcClient.sql(
            """
            SELECT id, workflow_instance_id, node_execution_id, event_type, event_message, event_detail, created_at
            FROM execution_event
            WHERE workflow_instance_id = ?
            ORDER BY id
            """
        )
            .param(workflowInstanceId)
            .query { rs, _ ->
                val nodeExecutionIdValue = rs.getObject("node_execution_id")
                ExecutionEvent(
                    id = rs.getLong("id"),
                    workflowInstanceId = rs.getLong("workflow_instance_id"),
                    nodeExecutionId = (nodeExecutionIdValue as? Number)?.toLong(),
                    eventType = ExecutionEventType.valueOf(rs.getString("event_type")),
                    eventMessage = rs.getString("event_message"),
                    eventDetail = rs.getString("event_detail"),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime()
                )
            }
            .list()
}

@Repository
class WorkflowTaskRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper
) {

    fun create(
        workflowInstanceId: Long,
        nodeId: String,
        input: Map<String, Any?>?,
        options: WorkflowTaskCreateOptions = WorkflowTaskCreateOptions()
    ): Long =
        jdbcClient.sql(
            """
            INSERT INTO workflow_task(
                workflow_instance_id, node_id, status, attempt_no, source_task_id, input_json, retry_reason,
                available_at, max_attempts, timeout_seconds, retry_backoff_seconds, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
            """
        )
            .param(workflowInstanceId)
            .param(nodeId)
            .param(TaskStatus.PENDING.name)
            .param(options.attemptNo)
            .param(options.sourceTaskId)
            .param(objectMapper.toJsonb(input))
            .param(options.retryReason)
            .param(options.availableAt)
            .param(options.maxAttempts)
            .param(options.timeoutSeconds)
            .param(options.retryBackoffSeconds)
            .query(Long::class.java)
            .single()

    fun claimNextAvailableTask(lockOwner: String): WorkflowTask? =
        jdbcClient.sql(
            """
            WITH candidate AS (
                SELECT t.id
                FROM workflow_task t
                JOIN workflow_instance i ON i.id = t.workflow_instance_id
                WHERE t.status = ? AND t.available_at <= CURRENT_TIMESTAMP
                  AND i.status IN (?, ?)
                ORDER BY t.id
                LIMIT 1
                FOR UPDATE OF t SKIP LOCKED
            )
            UPDATE workflow_task t
            SET status = ?, locked_at = CURRENT_TIMESTAMP, lock_owner = ?, updated_at = CURRENT_TIMESTAMP
            FROM candidate
            WHERE t.id = candidate.id
            RETURNING t.id, t.workflow_instance_id, t.node_id, t.status, t.attempt_no, t.source_task_id, t.input_json, t.retry_reason,
                      t.available_at, t.locked_at, t.lock_owner, t.error_message, t.max_attempts, t.timeout_seconds,
                      t.retry_backoff_seconds, t.created_at, t.updated_at
            """
        )
            .param(TaskStatus.PENDING.name)
            .param(WorkflowInstanceStatus.CREATED.name)
            .param(WorkflowInstanceStatus.RUNNING.name)
            .param(TaskStatus.RUNNING.name)
            .param(lockOwner)
            .query { rs, _ -> mapWorkflowTask(rs) }
            .optional()
            .orElse(null)

    fun markSucceeded(taskId: Long) {
        jdbcClient.sql(
            """
            UPDATE workflow_task
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = ?
            """
        )
            .param(TaskStatus.SUCCEEDED.name)
            .param(taskId)
            .param(TaskStatus.RUNNING.name)
            .update()
    }

    fun markFailed(taskId: Long, errorMessage: String) {
        jdbcClient.sql(
            """
            UPDATE workflow_task
            SET status = ?, error_message = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = ?
            """
        )
            .param(TaskStatus.FAILED.name)
            .param(errorMessage)
            .param(taskId)
            .param(TaskStatus.RUNNING.name)
            .update()
    }

    fun markCancelled(taskId: Long) {
        jdbcClient.sql(
            """
            UPDATE workflow_task
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status IN (?, ?)
            """
        )
            .param(TaskStatus.CANCELLED.name)
            .param(taskId)
            .param(TaskStatus.PENDING.name)
            .param(TaskStatus.RUNNING.name)
            .update()
    }

    fun findById(taskId: Long): WorkflowTask? =
        jdbcClient.sql(
            """
            SELECT id, workflow_instance_id, node_id, status, attempt_no, source_task_id, input_json, retry_reason,
                   available_at, locked_at, lock_owner, error_message, max_attempts, timeout_seconds,
                   retry_backoff_seconds, created_at, updated_at
            FROM workflow_task
            WHERE id = ?
            """
        )
            .param(taskId)
            .query { rs, _ -> mapWorkflowTask(rs) }
            .optional()
            .orElse(null)

    fun findByWorkflowInstanceId(workflowInstanceId: Long): List<WorkflowTask> =
        jdbcClient.sql(
            """
            SELECT id, workflow_instance_id, node_id, status, attempt_no, source_task_id, input_json, retry_reason,
                   available_at, locked_at, lock_owner, error_message, max_attempts, timeout_seconds,
                   retry_backoff_seconds, created_at, updated_at
            FROM workflow_task
            WHERE workflow_instance_id = ?
            ORDER BY id
            """
        )
            .param(workflowInstanceId)
            .query { rs, _ -> mapWorkflowTask(rs) }
            .list()

    fun findAll(status: TaskStatus? = null): List<WorkflowTask> {
        val sql = if (status == null) {
            """
            SELECT id, workflow_instance_id, node_id, status, attempt_no, source_task_id, input_json, retry_reason,
                   available_at, locked_at, lock_owner, error_message, max_attempts, timeout_seconds,
                   retry_backoff_seconds, created_at, updated_at
            FROM workflow_task
            ORDER BY id DESC
            """
        } else {
            """
            SELECT id, workflow_instance_id, node_id, status, attempt_no, source_task_id, input_json, retry_reason,
                   available_at, locked_at, lock_owner, error_message, max_attempts, timeout_seconds,
                   retry_backoff_seconds, created_at, updated_at
            FROM workflow_task
            WHERE status = ?
            ORDER BY id DESC
            """
        }
        val statement = jdbcClient.sql(sql)
        if (status != null) {
            statement.param(status.name)
        }
        return statement.query { rs, _ -> mapWorkflowTask(rs) }.list()
    }

    fun findLatestFailedTask(workflowInstanceId: Long): WorkflowTask? =
        jdbcClient.sql(
            """
            SELECT id, workflow_instance_id, node_id, status, attempt_no, source_task_id, input_json, retry_reason,
                   available_at, locked_at, lock_owner, error_message, max_attempts, timeout_seconds,
                   retry_backoff_seconds, created_at, updated_at
            FROM workflow_task
            WHERE workflow_instance_id = ? AND status = ?
            ORDER BY id DESC
            LIMIT 1
            """
        )
            .param(workflowInstanceId)
            .param(TaskStatus.FAILED.name)
            .query { rs, _ -> mapWorkflowTask(rs) }
            .optional()
            .orElse(null)

    fun requeueTask(
        workflowInstanceId: Long,
        nodeId: String,
        input: Map<String, Any?>?,
        options: WorkflowTaskCreateOptions = WorkflowTaskCreateOptions()
    ): Long =
        create(workflowInstanceId, nodeId, input, options)

    private fun mapWorkflowTask(rs: java.sql.ResultSet): WorkflowTask =
        WorkflowTask(
            id = rs.getLong("id"),
            workflowInstanceId = rs.getLong("workflow_instance_id"),
            nodeId = rs.getString("node_id"),
            status = TaskStatus.valueOf(rs.getString("status")),
            attemptNo = rs.getInt("attempt_no"),
            sourceTaskId = (rs.getObject("source_task_id") as? Number)?.toLong(),
            inputJson = rs.getString("input_json"),
            retryReason = rs.getString("retry_reason"),
            availableAt = rs.getTimestamp("available_at").toLocalDateTime(),
            lockedAt = rs.getTimestamp("locked_at")?.toLocalDateTime(),
            lockOwner = rs.getString("lock_owner"),
            errorMessage = rs.getString("error_message"),
            maxAttempts = rs.getInt("max_attempts"),
            timeoutSeconds = (rs.getObject("timeout_seconds") as? Number)?.toInt(),
            retryBackoffSeconds = (rs.getObject("retry_backoff_seconds") as? Number)?.toInt(),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
            updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
        )
}

@Repository
class FeedbackRecordRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper
) {

    fun create(
        workflowInstanceId: Long,
        nodeExecutionId: Long?,
        feedbackType: String,
        feedbackPayload: Map<String, Any?>?,
        createdBy: String?
    ): Long =
        jdbcClient.sql(
            """
            INSERT INTO feedback_record(
                workflow_instance_id, node_execution_id, feedback_type, feedback_payload, created_by, created_at
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            RETURNING id
            """
        )
            .param(workflowInstanceId)
            .param(nodeExecutionId)
            .param(feedbackType)
            .param(objectMapper.toJsonb(feedbackPayload))
            .param(createdBy)
            .query(Long::class.java)
            .single()

    fun findByWorkflowInstanceId(workflowInstanceId: Long): List<FeedbackRecord> =
        jdbcClient.sql(
            """
            SELECT id, workflow_instance_id, node_execution_id, feedback_type, feedback_payload, created_by, created_at
            FROM feedback_record
            WHERE workflow_instance_id = ?
            ORDER BY id
            """
        )
            .param(workflowInstanceId)
            .query { rs, _ ->
                val nodeExecutionIdValue = rs.getObject("node_execution_id")
                FeedbackRecord(
                    id = rs.getLong("id"),
                    workflowInstanceId = rs.getLong("workflow_instance_id"),
                    nodeExecutionId = (nodeExecutionIdValue as? Number)?.toLong(),
                    feedbackType = rs.getString("feedback_type"),
                    feedbackPayload = rs.getString("feedback_payload"),
                    createdBy = rs.getString("created_by"),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime()
                )
            }
            .list()
}
