package com.flowforge.api.runtime

import com.flowforge.runtime.application.RuntimeQueryService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/instances")
class RuntimeController(
    private val runtimeQueryService: RuntimeQueryService
) {

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
}
