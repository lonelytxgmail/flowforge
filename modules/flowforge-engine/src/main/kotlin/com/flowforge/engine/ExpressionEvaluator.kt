package com.flowforge.engine

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object ExpressionEvaluator {
    private val regex = Regex("\\{\\{\\s*(.*?)\\s*\\}\\}")
    private val objectMapper = jacksonObjectMapper()

    fun evaluate(config: Map<String, Any?>, contextData: Map<String, Any?>): Map<String, Any?> {
        return evaluateContext(config, contextData) as Map<String, Any?>
    }

    private fun evaluateContext(item: Any?, contextData: Map<String, Any?>): Any? {
        return when (item) {
            is Map<*, *> -> item.mapValues { evaluateContext(it.value, contextData) }
            is List<*> -> item.map { evaluateContext(it, contextData) }
            is String -> evaluateString(item, contextData)
            else -> item
        }
    }

    private fun evaluateString(text: String, contextData: Map<String, Any?>): Any {
        // 若全串匹配，则不走字符串化直接返回对象实体 (保持 Map / List / Boolean 类型以对应 API 请求中的强类型结构)
        if (text.trim().matches(Regex("^\\{\\{\\s*(.*?)\\s*\\}\\}\$"))) {
            val path = text.trim().removeSurrounding("{{", "}}").trim()
            val value = extractValueByPath(path, contextData)
            if (value != null) return value 
        }

        // 若是内嵌在 URL 等长串文本中，则进行字符串部分替换
        return regex.replace(text) { matchResult ->
            val path = matchResult.groupValues[1].trim()
            val value = extractValueByPath(path, contextData)
            if (value is Map<*, *> || value is List<*>) {
                objectMapper.writeValueAsString(value)
            } else {
                value?.toString() ?: matchResult.value
            }
        }
    }

    private fun extractValueByPath(path: String, contextData: Map<String, Any?>): Any? {
        val parts = path.split(".")
        var current: Any? = contextData
        for (part in parts) {
            if (current is Map<*, *>) {
                current = current[part]
            } else {
                return null
            }
        }
        return current
    }
}
