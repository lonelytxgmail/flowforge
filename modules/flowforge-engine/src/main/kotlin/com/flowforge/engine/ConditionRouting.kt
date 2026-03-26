package com.flowforge.engine

import com.flowforge.common.model.AppException
import com.flowforge.common.model.NodeType
import com.flowforge.workflow.domain.WorkflowDsl
import com.flowforge.workflow.domain.WorkflowEdgeDsl
import com.flowforge.workflow.domain.WorkflowNodeDsl
import org.springframework.stereotype.Component

/**
 * 第一阶段条件表达式只做“够用”的能力：
 * - `field == value`
 * - `field != value`
 * - `exists(field)`
 * - `not_exists(field)`
 * - `default`
 *
 * 支持简单的点路径，例如：`feedback.payload.approved == true`
 */
@Component
class WorkflowRoutingService(
    private val conditionExpressionEvaluator: ConditionExpressionEvaluator
) {

    fun resolveNextNodeId(
        dsl: WorkflowDsl,
        currentNode: WorkflowNodeDsl,
        output: Map<String, Any?>
    ): String {
        val outgoingEdges = dsl.edges.filter { it.from == currentNode.id }
        if (outgoingEdges.isEmpty()) {
            throw AppException("Next edge not found for node: ${currentNode.id}")
        }

        return if (currentNode.type == NodeType.CONDITION) {
            resolveConditionBranch(outgoingEdges, output, currentNode.id)
        } else {
            outgoingEdges.first().to
        }
    }

    private fun resolveConditionBranch(
        outgoingEdges: List<WorkflowEdgeDsl>,
        output: Map<String, Any?>,
        nodeId: String
    ): String {
        val matched = outgoingEdges.firstOrNull { edge ->
            val condition = edge.condition ?: return@firstOrNull false
            conditionExpressionEvaluator.evaluate(output, condition)
        }

        if (matched != null) {
            return matched.to
        }

        val defaultEdge = outgoingEdges.firstOrNull { it.condition == null || it.condition == "default" }
            ?: throw AppException("No matched condition edge and no default edge for node: $nodeId")

        return defaultEdge.to
    }
}

@Component
class ConditionExpressionEvaluator {

    fun evaluate(input: Map<String, Any?>, expression: String): Boolean {
        val normalized = expression.trim()

        if (normalized == "default") {
            return true
        }
        if (normalized.startsWith("exists(") && normalized.endsWith(")")) {
            val path = normalized.removePrefix("exists(").removeSuffix(")")
            return readPath(input, path) != null
        }
        if (normalized.startsWith("not_exists(") && normalized.endsWith(")")) {
            val path = normalized.removePrefix("not_exists(").removeSuffix(")")
            return readPath(input, path) == null
        }
        if (normalized.contains("==")) {
            val parts = normalized.split("==", limit = 2)
            return compareValue(input, parts[0].trim(), parts[1].trim(), equals = true)
        }
        if (normalized.contains("!=")) {
            val parts = normalized.split("!=", limit = 2)
            return compareValue(input, parts[0].trim(), parts[1].trim(), equals = false)
        }

        throw AppException("Unsupported condition expression: $expression")
    }

    private fun compareValue(
        input: Map<String, Any?>,
        path: String,
        rawExpected: String,
        equals: Boolean
    ): Boolean {
        val actual = readPath(input, path)
        val expected = parseLiteral(rawExpected)
        return if (equals) actual == expected else actual != expected
    }

    private fun parseLiteral(rawValue: String): Any? {
        val trimmed = rawValue.trim().removeSurrounding("\"")
        return when {
            trimmed.equals("true", ignoreCase = true) -> true
            trimmed.equals("false", ignoreCase = true) -> false
            trimmed.equals("null", ignoreCase = true) -> null
            trimmed.toLongOrNull() != null -> trimmed.toLong()
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> trimmed
        }
    }

    private fun readPath(input: Map<String, Any?>, path: String): Any? {
        var current: Any? = input
        for (segment in path.split(".")) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                else -> return null
            }
        }
        return current
    }
}
