package com.flowforge.runtime.application

/**
 * runtime 只依赖一个“查询当前实例对应 DSL 信息”的抽象接口。
 */
interface WorkflowDefinitionRuntimeGateway {
    fun getNodeTypeName(instanceId: Long, nodeId: String): String

    fun getNextNodeId(instanceId: Long, nodeId: String): String
}
