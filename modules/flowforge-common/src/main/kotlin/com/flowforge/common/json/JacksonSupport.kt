package com.flowforge.common.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.postgresql.util.PGobject

/**
 * 统一的 ObjectMapper 工厂。
 *
 * 你后面会频繁看到它，因为：
 * 1. DSL 要转 JSON
 * 2. input / output / context 要进 PostgreSQL JSONB
 */
fun createObjectMapper(): ObjectMapper =
    jacksonObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

fun ObjectMapper.toJson(value: Any?): String = writeValueAsString(value)

fun ObjectMapper.toJsonb(value: Any?): PGobject? {
    if (value == null) {
        return null
    }

    val jsonObject = PGobject()
    jsonObject.type = "jsonb"
    jsonObject.value = toJson(value)
    return jsonObject
}
