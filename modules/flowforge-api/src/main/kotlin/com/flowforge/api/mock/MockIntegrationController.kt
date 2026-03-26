package com.flowforge.api.mock

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 这些接口只用于本地联调和节点能力验证。
 *
 * 价值在于：
 * - 不依赖外部第三方系统
 * - 可以稳定验证 REST / 登录会话 / 数字员工节点
 */
@RestController
@RequestMapping("/api/mock")
class MockIntegrationController {

    @PostMapping("/login")
    fun login(@RequestBody payload: Map<String, Any?>): Map<String, Any?> {
        val username = payload["username"]?.toString() ?: "unknown"
        return mapOf(
            "token" to "mock-token-$username",
            "tokenType" to "Bearer"
        )
    }

    @PostMapping("/echo")
    fun echo(
        @RequestHeader(required = false, name = "Authorization") authorization: String?,
        @RequestBody payload: Map<String, Any?>
    ): Map<String, Any?> =
        mapOf(
            "authorization" to authorization,
            "payload" to payload
        )

    @GetMapping(value = ["/stream"], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun stream(): String =
        listOf(
            "chunk-1: hello",
            "chunk-2: from",
            "chunk-3: flowforge"
        ).joinToString(separator = "\n")
}
