package com.flowforge.api.workflow

import com.flowforge.engine.StartWorkflowCommand
import com.flowforge.engine.WorkflowEngineService
import com.flowforge.common.model.NodeType
import com.flowforge.workflow.application.CreateWorkflowCommand
import com.flowforge.workflow.application.PublishWorkflowVersionCommand
import com.flowforge.workflow.application.SaveNodeTemplateCommand
import com.flowforge.workflow.application.UpdateNodeTemplateCommand
import com.flowforge.workflow.application.WorkflowDefinitionService
import com.flowforge.workflow.domain.WorkflowDsl
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.DeleteMapping

data class CreateWorkflowRequest(
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    val description: String?
)

data class PublishWorkflowVersionRequest(
    val dsl: WorkflowDsl
)

data class StartWorkflowRequest(
    val inputPayload: Map<String, Any?>? = null
)

data class SaveNodeTemplateRequest(
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    val description: String?,
    val groupName: String?,
    val nodeType: NodeType,
    val nodeConfig: Map<String, Any?>? = null
)

@Validated
@RestController
@RequestMapping("/api/workflows")
class WorkflowController(
    private val workflowDefinitionService: WorkflowDefinitionService,
    private val workflowEngineService: WorkflowEngineService
) {

    @Operation(summary = "查询工作流定义列表")
    @GetMapping
    fun listWorkflows() =
        workflowDefinitionService.listWorkflowDefinitions()

    @Operation(summary = "查询工作流定义详情")
    @GetMapping("/{workflowDefinitionId}")
    fun getWorkflow(@PathVariable workflowDefinitionId: Long) =
        workflowDefinitionService.getWorkflowDefinition(workflowDefinitionId)

    @Operation(summary = "查询工作流版本列表")
    @GetMapping("/{workflowDefinitionId}/versions")
    fun listVersions(@PathVariable workflowDefinitionId: Long) =
        workflowDefinitionService.listWorkflowVersions(workflowDefinitionId)

    @Operation(summary = "创建工作流定义")
    @PostMapping
    fun createWorkflow(@Valid @RequestBody request: CreateWorkflowRequest) =
        workflowDefinitionService.createWorkflow(
            CreateWorkflowCommand(
                code = request.code,
                name = request.name,
                description = request.description
            )
        )

    @Operation(summary = "发布工作流版本")
    @PostMapping("/{workflowDefinitionId}/versions")
    fun publishVersion(
        @PathVariable workflowDefinitionId: Long,
        @RequestBody request: PublishWorkflowVersionRequest
    ) = workflowDefinitionService.publishVersion(
        PublishWorkflowVersionCommand(
            workflowDefinitionId = workflowDefinitionId,
            dsl = request.dsl
        )
    )

    @Operation(summary = "启动工作流实例")
    @PostMapping("/{workflowDefinitionId}/instances")
    fun startWorkflow(
        @PathVariable workflowDefinitionId: Long,
        @RequestBody(required = false) request: StartWorkflowRequest?
    ): Map<String, Any> {
        val instanceId = workflowEngineService.startWorkflow(
            StartWorkflowCommand(
                workflowDefinitionId = workflowDefinitionId,
                inputPayload = request?.inputPayload
            )
        )

        return mapOf("instanceId" to instanceId)
    }

    @Operation(summary = "查询节点模板列表")
    @GetMapping("/node-templates")
    fun listNodeTemplates() =
        workflowDefinitionService.listNodeTemplates()

    @Operation(summary = "查询节点模板详情")
    @GetMapping("/node-templates/{nodeTemplateId}")
    fun getNodeTemplate(@PathVariable nodeTemplateId: Long) =
        workflowDefinitionService.getNodeTemplate(nodeTemplateId)

    @Operation(summary = "保存节点模板")
    @PostMapping("/node-templates")
    fun saveNodeTemplate(@Valid @RequestBody request: SaveNodeTemplateRequest) =
        workflowDefinitionService.saveNodeTemplate(
            SaveNodeTemplateCommand(
                code = request.code,
                name = request.name,
                description = request.description,
                groupName = request.groupName,
                nodeType = request.nodeType,
                nodeConfig = request.nodeConfig ?: emptyMap()
            )
        )

    @Operation(summary = "更新节点模板")
    @PutMapping("/node-templates/{nodeTemplateId}")
    fun updateNodeTemplate(
        @PathVariable nodeTemplateId: Long,
        @Valid @RequestBody request: SaveNodeTemplateRequest
    ) = workflowDefinitionService.updateNodeTemplate(
        UpdateNodeTemplateCommand(
            id = nodeTemplateId,
            code = request.code,
            name = request.name,
            description = request.description,
            groupName = request.groupName,
            nodeType = request.nodeType,
            nodeConfig = request.nodeConfig ?: emptyMap()
        )
    )

    @Operation(summary = "删除节点模板")
    @DeleteMapping("/node-templates/{nodeTemplateId}")
    fun deleteNodeTemplate(@PathVariable nodeTemplateId: Long): Map<String, Any> {
        workflowDefinitionService.deleteNodeTemplate(nodeTemplateId)
        return mapOf("deleted" to true, "id" to nodeTemplateId)
    }
}
