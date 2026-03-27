package com.flowforge.api.workflow

import com.flowforge.common.model.NodeType
import com.flowforge.engine.NodeExecutionContext
import com.flowforge.engine.NodeExecutorRegistry
import com.flowforge.workflow.domain.NodeConfigValidator
import com.flowforge.workflow.domain.WorkflowNodeDsl
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import com.flowforge.workflow.domain.WorkflowDsl
import com.flowforge.common.model.AppException
import com.flowforge.engine.NodeExecutionOutcome

data class RunNodeDebugRequest(
    val nodeType: NodeType,
    val nodeConfig: Map<String, Any?>,
    val inputPayload: Map<String, Any?>? = null,
    val workflowContext: Map<String, Any?>? = null
)

data class RunFlowDebugRequest(
    val dsl: WorkflowDsl,
    val inputPayload: Map<String, Any?>? = null
)

@RestController
@RequestMapping("/api/workflows/debug")
class WorkflowDebugController(
    private val nodeExecutorRegistry: NodeExecutorRegistry
) {

    @Operation(summary = "单步沙箱试运行节点", description = "用于验证单一原子组件而无需保存或启动完整流程")
    @PostMapping("/run-node")
    fun runNodeDebug(@RequestBody request: RunNodeDebugRequest): Map<String, Any?> {
        // 1. 验证配置合法性 (复用我们在阶段三打造的强类型校验矩阵)
        NodeConfigValidator.validateNodeConfig(request.nodeType, request.nodeConfig)

        // 2. 拼接一个临时的 DSL Node 结构
        val simulatedNode = WorkflowNodeDsl(
            id = "debug-node-1",
            name = "Debug Node",
            type = request.nodeType,
            config = request.nodeConfig
        )
        
        // 3. 构建虚假的执行上下文 (InstanceId = -1 表明是沙盒调用)
        val context = NodeExecutionContext(
            workflowInstanceId = -1L,
            node = simulatedNode,
            input = request.inputPayload ?: emptyMap(),
            workflowContext = request.workflowContext ?: emptyMap()
        )
        
        // 4. 获取执行器并脱库直接同步运行
        val executor = nodeExecutorRegistry.getExecutor(request.nodeType)
        val result = executor.execute(context)
        
        return mapOf(
            "outcome" to result.outcome.name,
            "output" to result.output,
            "contextUpdates" to result.contextUpdates
        )
    }

    @Operation(summary = "沙箱模拟全流程遍历", description = "用于支持图式编排的无实例一键运行全测")
    @PostMapping("/run-flow")
    fun runFlowDebug(@RequestBody request: RunFlowDebugRequest): Map<String, Any?> {
        val dsl = request.dsl
        var currentNodeId = dsl.nodes.firstOrNull { it.type == NodeType.START }?.id 
            ?: throw AppException("DSL requires a START node")
            
        val executionLog = mutableListOf<Map<String, Any?>>()
        val contextUpdates = mutableMapOf<String, Any?>()
        var currentInput = request.inputPayload ?: emptyMap()

        // 简易单线程顺序引擎沙盒
        while (true) {
            val node = dsl.nodes.find { it.id == currentNodeId } ?: break
            
            // 构造无状态环境
            val execContext = NodeExecutionContext(
                workflowInstanceId = -1L,
                node = node,
                input = currentInput,
                workflowContext = contextUpdates
            )
            
            val executor = nodeExecutorRegistry.getExecutor(node.type)
            val result = try {
                executor.execute(execContext)
            } catch (ex: Exception) {
                executionLog.add(mapOf(
                    "nodeId" to node.id,
                    "nodeName" to node.name,
                    "status" to "FAILED",
                    "error" to (ex.message ?: ex.toString())
                ))
                break
            }
            
            executionLog.add(mapOf(
                "nodeId" to node.id,
                "nodeName" to node.name,
                "type" to node.type.name,
                "input" to currentInput,
                "output" to result.output,
                "status" to "SUCCESS"
            ))

            contextUpdates.putAll(result.contextUpdates)
            currentInput = result.output

            if (node.type == NodeType.END) break
            if (result.outcome == NodeExecutionOutcome.WAITING) {
                executionLog.add(mapOf(
                    "status" to "PAUSED",
                    "message" to "Debug sandbox stopped due to WAITING state."
                ))
                break
            }

            // 寻找唯一出度的下游边进行流转 (暂不解析 Condition 表达)
            val nextEdge = dsl.edges.firstOrNull { it.from == node.id }
            if (nextEdge == null) break
            currentNodeId = nextEdge.to
        }
        
        return mapOf("executionLog" to executionLog)
    }
}
