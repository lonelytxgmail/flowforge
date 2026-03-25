package com.flowforge.runtime.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowforge.common.json.toJsonb
import com.flowforge.common.model.ExecutionEventType
import com.flowforge.common.model.NodeExecutionStatus
import com.flowforge.common.model.WorkflowInstanceStatus
import com.flowforge.runtime.domain.ExecutionEvent
import com.flowforge.runtime.domain.NodeExecution
import com.flowforge.runtime.domain.WorkflowInstance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class WorkflowInstanceRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper
) {

    fun create(
        workflowDefinitionId: Long,
        workflowVersionId: Long,
        inputPayload: Map<String, Any?>?
    ): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcClient.sql(
            """
            INSERT INTO workflow_instance(
                workflow_definition_id, workflow_version_id, status, input_payload, context_json, current_node_id, started_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """
        )
            .param(workflowDefinitionId)
            .param(workflowVersionId)
            .param(WorkflowInstanceStatus.CREATED.name)
            .param(objectMapper.toJsonb(inputPayload))
            .param(objectMapper.toJsonb(emptyMap<String, Any?>()))
            .param(null)
            .update(keyHolder)

        return keyHolder.key!!.toLong()
    }

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
    ): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcClient.sql(
            """
            INSERT INTO node_execution(
                workflow_instance_id, node_id, node_name, node_type, status, attempt_no, input_json, started_at
            ) VALUES (?, ?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP)
            """
        )
            .param(workflowInstanceId)
            .param(nodeId)
            .param(nodeName)
            .param(nodeType)
            .param(NodeExecutionStatus.RUNNING.name)
            .param(objectMapper.toJsonb(input))
            .update(keyHolder)

        return keyHolder.key!!.toLong()
    }

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
