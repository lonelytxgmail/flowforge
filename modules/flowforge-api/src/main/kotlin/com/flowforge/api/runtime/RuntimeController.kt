package com.flowforge.api.runtime

import com.flowforge.runtime.application.RuntimeCommandService
import com.flowforge.runtime.application.RuntimeQueryService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SubmitFeedbackRequest(
    val feedbackType: String = "MANUAL_FEEDBACK",
    val feedbackPayload: Map<String, Any?>? = null,
    val createdBy: String? = null
)

data class RetryTaskRequest(
    val reason: String? = null
)

@RestController
@RequestMapping("/api/instances")
class RuntimeController(
    private val runtimeQueryService: RuntimeQueryService,
    private val runtimeCommandService: RuntimeCommandService
) {

    @Operation(summary = "查询实例列表")
    @GetMapping
    fun listInstances() =
        runtimeQueryService.listInstances()

    @Operation(summary = "查询实例状态")
    @GetMapping("/{instanceId}")
    fun getInstance(@PathVariable instanceId: Long) =
        runtimeQueryService.getInstance(instanceId)

    @Operation(summary = "查询节点执行明细")
    @GetMapping("/{instanceId}/node-executions")
    fun getNodeExecutions(@PathVariable instanceId: Long) =
        runtimeQueryService.getNodeExecutions(instanceId)

    @Operation(summary = "查询执行事件日志")
    @GetMapping("/{instanceId}/events")
    fun getExecutionEvents(@PathVariable instanceId: Long) =
        runtimeQueryService.getExecutionEvents(instanceId)

    @Operation(summary = "查询实例任务链路")
    @GetMapping("/{instanceId}/tasks")
    fun getWorkflowTasks(@PathVariable instanceId: Long) =
        runtimeQueryService.getWorkflowTasks(instanceId)

    @Operation(summary = "查询任务列表")
    @GetMapping("/tasks")
    fun listWorkflowTasks(@RequestParam(required = false) status: String?) =
        runtimeQueryService.listWorkflowTasks(status)

    @Operation(summary = "查询反馈记录")
    @GetMapping("/{instanceId}/feedback-records")
    fun getFeedbackRecords(@PathVariable instanceId: Long) =
        runtimeQueryService.getFeedbackRecords(instanceId)

    @Operation(summary = "暂停实例")
    @PostMapping("/{instanceId}/pause")
    fun pauseInstance(@PathVariable instanceId: Long): Map<String, Any> {
        runtimeCommandService.pauseInstance(instanceId)
        return mapOf("instanceId" to instanceId, "status" to "PAUSED")
    }

    @Operation(summary = "恢复实例")
    @PostMapping("/{instanceId}/resume")
    fun resumeInstance(@PathVariable instanceId: Long): Map<String, Any> {
        runtimeCommandService.resumeInstance(instanceId)
        return mapOf("instanceId" to instanceId, "status" to "RUNNING")
    }

    @Operation(summary = "重试失败实例")
    @PostMapping("/{instanceId}/retry")
    fun retryInstance(@PathVariable instanceId: Long): Map<String, Any> {
        runtimeCommandService.retryInstance(instanceId)
        return mapOf("instanceId" to instanceId, "status" to "RUNNING")
    }

    @Operation(summary = "重试失败任务")
    @PostMapping("/{instanceId}/tasks/{taskId}/retry")
    fun retryTask(
        @PathVariable instanceId: Long,
        @PathVariable taskId: Long,
        @RequestBody(required = false) request: RetryTaskRequest?
    ): Map<String, Any> {
        val retryTaskId = runtimeCommandService.retryTask(
            instanceId = instanceId,
            taskId = taskId,
            reason = request?.reason
        )
        return mapOf("instanceId" to instanceId, "status" to "RUNNING", "taskId" to retryTaskId)
    }

    @Operation(summary = "提交反馈并继续执行")
    @PostMapping("/{instanceId}/feedback")
    fun submitFeedback(
        @PathVariable instanceId: Long,
        @RequestBody request: SubmitFeedbackRequest
    ): Map<String, Any> {
        runtimeCommandService.submitFeedback(
            instanceId = instanceId,
            feedbackType = request.feedbackType,
            feedbackPayload = request.feedbackPayload,
            createdBy = request.createdBy
        )
        return mapOf("instanceId" to instanceId, "status" to "RUNNING")
    }
}
