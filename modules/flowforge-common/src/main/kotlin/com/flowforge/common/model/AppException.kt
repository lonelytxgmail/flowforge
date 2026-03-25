package com.flowforge.common.model

/**
 * 用一个简单的运行时异常封装业务错误，先保持直接和易懂。
 */
class AppException(message: String) : RuntimeException(message)

