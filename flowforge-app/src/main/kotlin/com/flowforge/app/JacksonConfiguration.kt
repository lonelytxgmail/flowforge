package com.flowforge.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowforge.common.json.createObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 把 ObjectMapper 注册成 Spring Bean。
 *
 * 之所以放在 app 模块，而不是 common 模块：
 * - common 应该尽量保持“无框架污染”
 * - app 才是负责组装 Spring 容器的地方
 */
@Configuration
class JacksonConfiguration {

    @Bean
    fun objectMapper(): ObjectMapper = createObjectMapper()
}
