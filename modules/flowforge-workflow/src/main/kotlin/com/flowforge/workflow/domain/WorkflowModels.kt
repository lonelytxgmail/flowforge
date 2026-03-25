package com.flowforge.workflow.domain

import com.flowforge.common.model.NodeType
import com.flowforge.common.model.WorkflowDefinitionStatus
import com.flowforge.common.model.WorkflowVersionStatus
import java.time.LocalDateTime

data class WorkflowDefinition(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val status: WorkflowDefinitionStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class WorkflowVersion(
    val id: Long,
    val workflowDefinitionId: Long,
    val versionNo: Int,
    val status: WorkflowVersionStatus,
    val dsl: WorkflowDsl,
    val publishedAt: LocalDateTime?,
    val createdAt: LocalDateTime
)

/**
 * 第一阶段 DSL 先用 JSON 表示。
 *
 * `data class` 是 Kotlin 很常见的建模方式：
 * 它会自动帮你生成 equals/hashCode/toString，写业务模型很省力。
 */
data class WorkflowDsl(
    val version: String = "1.0",
    val nodes: List<WorkflowNodeDsl>,
    val edges: List<WorkflowEdgeDsl>
)

data class WorkflowNodeDsl(
    val id: String,
    val name: String,
    val type: NodeType,
    val config: Map<String, Any?> = emptyMap()
)

data class WorkflowEdgeDsl(
    val from: String,
    val to: String,
    val condition: String? = null
)

