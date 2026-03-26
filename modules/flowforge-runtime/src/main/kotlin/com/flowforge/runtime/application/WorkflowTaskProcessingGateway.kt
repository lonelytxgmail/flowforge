package com.flowforge.runtime.application

/**
 * runtime 模块只知道“有人可以继续处理任务”，
 * 不直接依赖 engine 里的具体 worker 实现。
 */
interface WorkflowTaskProcessingGateway {
    fun processAvailableTasks(maxTasks: Int)
}
