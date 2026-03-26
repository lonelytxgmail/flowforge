package com.flowforge.engine

import com.flowforge.common.model.NodeType
import com.flowforge.workflow.domain.WorkflowNodeDsl
import org.springframework.stereotype.Component

data class NodeExecutionContext(
    val workflowInstanceId: Long,
    val node: WorkflowNodeDsl,
    val input: Map<String, Any?>,
    val workflowContext: Map<String, Any?> = emptyMap()
)

enum class NodeExecutionOutcome {
    COMPLETED,
    WAITING
}

data class NodeExecutionResult(
    val output: Map<String, Any?>,
    val outcome: NodeExecutionOutcome = NodeExecutionOutcome.COMPLETED,
    val contextUpdates: Map<String, Any?> = emptyMap()
)

/**
 * 每种节点类型对应一个执行器。
 *
 * 这样做的好处是：
 * - 引擎只负责“编排”
 * - 执行器只负责“这个节点怎么跑”
 * - 后续加数字员工 / LLM / Agent 时，不需要重写引擎核心
 */
interface NodeExecutor {
    fun supports(nodeType: NodeType): Boolean

    fun execute(context: NodeExecutionContext): NodeExecutionResult
}

@Component
class NodeExecutorRegistry(
    executors: List<NodeExecutor>
) {
    private val executorsByType: Map<NodeType, NodeExecutor> =
        executors.associateBy { executor ->
            NodeType.entries.first { executor.supports(it) }
        }

    fun getExecutor(nodeType: NodeType): NodeExecutor =
        executorsByType[nodeType]
            ?: error("No NodeExecutor registered for node type: $nodeType")
}

@Component
class StartNodeExecutor : NodeExecutor {
    override fun supports(nodeType: NodeType): Boolean = nodeType == NodeType.START

    override fun execute(context: NodeExecutionContext): NodeExecutionResult =
        NodeExecutionResult(output = context.input)
}

@Component
class EndNodeExecutor : NodeExecutor {
    override fun supports(nodeType: NodeType): Boolean = nodeType == NodeType.END

    override fun execute(context: NodeExecutionContext): NodeExecutionResult =
        NodeExecutionResult(output = context.input)
}

@Component
class WaitForFeedbackNodeExecutor : NodeExecutor {
    override fun supports(nodeType: NodeType): Boolean = nodeType == NodeType.WAIT_FOR_FEEDBACK

    override fun execute(context: NodeExecutionContext): NodeExecutionResult =
        NodeExecutionResult(
            output = context.input,
            outcome = NodeExecutionOutcome.WAITING
        )
}
