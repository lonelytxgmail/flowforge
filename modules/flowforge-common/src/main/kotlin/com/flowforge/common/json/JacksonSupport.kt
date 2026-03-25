package com.flowforge.common.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 统一的 ObjectMapper。
 *
 * 你后面会频繁看到它，因为：
 * 1. DSL 要转 JSON
 * 2. input / output / context 要进 PostgreSQL JSONB
 */
@Configuration
class JacksonSupport {

    @Bean
    fun objectMapper(): ObjectMapper =
        jacksonObjectMapper()
            .registerKotlinModule()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

fun ObjectMapper.toJson(value: Any?): String = writeValueAsString(value)

fun ObjectMapper.toJsonb(value: Any?): PGobject {
    val jsonObject = PGobject()
    jsonObject.type = "jsonb"
    jsonObject.value = toJson(value)
    return jsonObject
}

