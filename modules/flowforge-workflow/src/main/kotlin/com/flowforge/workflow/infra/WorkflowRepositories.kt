package com.flowforge.workflow.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowforge.common.json.toJsonb
import com.flowforge.common.model.WorkflowDefinitionStatus
import com.flowforge.common.model.NodeType
import com.flowforge.common.model.WorkflowVersionStatus
import com.flowforge.workflow.domain.NodeTemplate
import com.flowforge.workflow.domain.WorkflowDefinition
import com.flowforge.workflow.domain.WorkflowDsl
import com.flowforge.workflow.domain.WorkflowVersion
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class WorkflowDefinitionRepository(
    private val jdbcClient: JdbcClient
) {

    fun save(code: String, name: String, description: String?): Long =
        jdbcClient.sql(
            """
            INSERT INTO workflow_definition(code, name, description, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
            """
        )
            .param(code)
            .param(name)
            .param(description)
            .param(WorkflowDefinitionStatus.DRAFT.name)
            .query(Long::class.java)
            .single()

    fun findById(id: Long): WorkflowDefinition? =
        jdbcClient.sql(
            """
            SELECT id, code, name, description, status, created_at, updated_at
            FROM workflow_definition
            WHERE id = ?
            """
        )
            .param(id)
            .query { rs, _ ->
                WorkflowDefinition(
                    id = rs.getLong("id"),
                    code = rs.getString("code"),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    status = WorkflowDefinitionStatus.valueOf(rs.getString("status")),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                    updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
                )
            }
            .optional()
            .orElse(null)

    fun findAll(): List<WorkflowDefinition> =
        jdbcClient.sql(
            """
            SELECT id, code, name, description, status, created_at, updated_at
            FROM workflow_definition
            ORDER BY id DESC
            """
        )
            .query { rs, _ ->
                WorkflowDefinition(
                    id = rs.getLong("id"),
                    code = rs.getString("code"),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    status = WorkflowDefinitionStatus.valueOf(rs.getString("status")),
                    createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                    updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
                )
            }
            .list()
}

@Repository
class WorkflowVersionRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper
) {

    fun nextVersionNo(workflowDefinitionId: Long): Int =
        jdbcClient.sql(
            """
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM workflow_version
            WHERE workflow_definition_id = ?
            """
        )
            .param(workflowDefinitionId)
            .query(Int::class.java)
            .single()

    fun savePublishedVersion(workflowDefinitionId: Long, versionNo: Int, dsl: WorkflowDsl): Long {
        val versionId = jdbcClient.sql(
            """
            INSERT INTO workflow_version(
                workflow_definition_id, version_no, status, dsl_json, published_at, created_at
            ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
            """
        )
            .param(workflowDefinitionId)
            .param(versionNo)
            .param(WorkflowVersionStatus.PUBLISHED.name)
            .param(objectMapper.toJsonb(dsl))
            .query(Long::class.java)
            .single()

        jdbcClient.sql(
            """
            UPDATE workflow_definition
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """
        )
            .param(WorkflowDefinitionStatus.ACTIVE.name)
            .param(workflowDefinitionId)
            .update()

        return versionId
    }

    fun findById(id: Long): WorkflowVersion? =
        jdbcClient.sql(
            """
            SELECT id, workflow_definition_id, version_no, status, dsl_json, published_at, created_at
            FROM workflow_version
            WHERE id = ?
            """
        )
            .param(id)
            .query { rs, _ -> mapVersion(rs.getLong("id"), rs) }
            .optional()
            .orElse(null)

    fun findLatestPublishedVersion(workflowDefinitionId: Long): WorkflowVersion? =
        jdbcClient.sql(
            """
            SELECT id, workflow_definition_id, version_no, status, dsl_json, published_at, created_at
            FROM workflow_version
            WHERE workflow_definition_id = ? AND status = ?
            ORDER BY version_no DESC
            LIMIT 1
            """
        )
            .param(workflowDefinitionId)
            .param(WorkflowVersionStatus.PUBLISHED.name)
            .query { rs, _ -> mapVersion(rs.getLong("id"), rs) }
            .optional()
            .orElse(null)

    fun findByWorkflowDefinitionId(workflowDefinitionId: Long): List<WorkflowVersion> =
        jdbcClient.sql(
            """
            SELECT id, workflow_definition_id, version_no, status, dsl_json, published_at, created_at
            FROM workflow_version
            WHERE workflow_definition_id = ?
            ORDER BY version_no DESC
            """
        )
            .param(workflowDefinitionId)
            .query { rs, _ -> mapVersion(rs.getLong("id"), rs) }
            .list()

    private fun mapVersion(id: Long, rs: java.sql.ResultSet): WorkflowVersion =
        WorkflowVersion(
            id = id,
            workflowDefinitionId = rs.getLong("workflow_definition_id"),
            versionNo = rs.getInt("version_no"),
            status = WorkflowVersionStatus.valueOf(rs.getString("status")),
            dsl = objectMapper.readValue(rs.getString("dsl_json"), WorkflowDsl::class.java),
            publishedAt = rs.getTimestamp("published_at")?.toLocalDateTime(),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime()
        )
}

@Repository
class NodeTemplateRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper
) {

    fun save(
        code: String,
        name: String,
        description: String?,
        nodeType: NodeType,
        nodeConfig: Map<String, Any?>
    ): Long =
        jdbcClient.sql(
            """
            INSERT INTO node_template(code, name, description, node_type, node_config, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
            """
        )
            .param(code)
            .param(name)
            .param(description)
            .param(nodeType.name)
            .param(objectMapper.toJsonb(nodeConfig))
            .query(Long::class.java)
            .single()

    fun update(
        id: Long,
        code: String,
        name: String,
        description: String?,
        nodeType: NodeType,
        nodeConfig: Map<String, Any?>
    ) {
        jdbcClient.sql(
            """
            UPDATE node_template
            SET code = ?, name = ?, description = ?, node_type = ?, node_config = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """
        )
            .param(code)
            .param(name)
            .param(description)
            .param(nodeType.name)
            .param(objectMapper.toJsonb(nodeConfig))
            .param(id)
            .update()
    }

    fun findById(id: Long): NodeTemplate? =
        jdbcClient.sql(
            """
            SELECT id, code, name, description, node_type, node_config, created_at, updated_at
            FROM node_template
            WHERE id = ?
            """
        )
            .param(id)
            .query { rs, _ -> mapNodeTemplate(rs) }
            .optional()
            .orElse(null)

    fun findAll(): List<NodeTemplate> =
        jdbcClient.sql(
            """
            SELECT id, code, name, description, node_type, node_config, created_at, updated_at
            FROM node_template
            ORDER BY id DESC
            """
        )
            .query { rs, _ -> mapNodeTemplate(rs) }
            .list()

    fun delete(id: Long) {
        jdbcClient.sql("DELETE FROM node_template WHERE id = ?")
            .param(id)
            .update()
    }

    private fun mapNodeTemplate(rs: java.sql.ResultSet): NodeTemplate =
        NodeTemplate(
            id = rs.getLong("id"),
            code = rs.getString("code"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            nodeType = NodeType.valueOf(rs.getString("node_type")),
            nodeConfig = objectMapper.readValue(
                rs.getString("node_config"),
                objectMapper.typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java)
            ) as Map<String, Any?>,
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
            updatedAt = rs.getTimestamp("updated_at").toLocalDateTime()
        )
}
