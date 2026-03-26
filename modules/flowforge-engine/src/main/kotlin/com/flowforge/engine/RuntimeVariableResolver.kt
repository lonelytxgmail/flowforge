package com.flowforge.engine

import org.springframework.stereotype.Component

/**
 * 第一阶段变量解析保持轻量：
 * - `{{ input.xxx }}`
 * - `{{ context.xxx }}`
 * - `{{ steps.nodeId.output.xxx }}`
 * - `{{ system.instanceId }}`
 *
 * 规则：
 * - 如果整个字符串就是一个变量占位符，保留原始类型
 * - 如果变量嵌在更长的字符串里，则按字符串替换
 */
@Component
class RuntimeVariableResolver {
    private val variablePattern = Regex("\\{\\{\\s*([^}]+?)\\s*}}")

    fun resolveValue(value: Any?, scope: Map<String, Any?>): Any? =
        when (value) {
            is String -> resolveString(value, scope)
            is Map<*, *> -> resolveMap(value, scope)
            is List<*> -> value.map { resolveValue(it, scope) }
            else -> value
        }

    fun resolveMap(input: Map<*, *>, scope: Map<String, Any?>): Map<String, Any?> =
        input.entries.associate { (key, value) ->
            key.toString() to resolveValue(value, scope)
        }

    private fun resolveString(text: String, scope: Map<String, Any?>): Any? {
        val wholeMatch = variablePattern.matchEntire(text)
        if (wholeMatch != null) {
            return readPath(scope, wholeMatch.groupValues[1].trim())
        }

        return variablePattern.replace(text) { match ->
            val resolved = readPath(scope, match.groupValues[1].trim())
            resolved?.toString() ?: ""
        }
    }

    private fun readPath(scope: Map<String, Any?>, path: String): Any? {
        var current: Any? = scope
        for (segment in path.split(".")) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                is List<*> -> {
                    val currentList = current
                    segment.toIntOrNull()?.let { index -> currentList.getOrNull(index) }
                }
                else -> return null
            }
        }
        return current
    }
}
