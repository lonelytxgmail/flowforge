package com.flowforge.workflow.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.flowforge.common.model.AppException
import com.flowforge.common.model.NodeType

val nodeConfigMapper = jacksonObjectMapper()

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestNodeConfig(
    val url: String?,
    val method: String? = "GET",
    val headers: Map<String, String>? = emptyMap(),
    val queryParams: Map<String, String>? = emptyMap(),
    val body: Any? = null,
    val auth: RestAuthConfig? = null,
    val streaming: Boolean? = false
) {
    init {
        require(!url.isNullOrBlank()) { "REST node requires config.url" }
        require(!method.isNullOrBlank()) { "REST node requires config.method" }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestAuthConfig(
    val type: String?,
    val token: String? = null,
    val username: String? = null,
    val password: String? = null,
    val loginUrl: String? = null,
    val loginMethod: String? = "POST",
    val loginBody: Any? = null,
    val tokenPath: String? = "token",
    val sessionContextKey: String? = "authSession",
    val headerName: String? = "Authorization",
    val prefix: String? = "Bearer"
) {
    init {
        when (type) {
            "bearer_static" -> require(!token.isNullOrBlank()) { "auth.token is required for bearer_static" }
            "login_session" -> require(!loginUrl.isNullOrBlank()) { "auth.loginUrl is required for login_session" }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DatabaseNodeConfig(
    val jdbcUrl: String?,
    val username: String? = null,
    val password: String? = null,
    val sql: String?,
    val operation: String? = "QUERY"
) {
    init {
        require(!jdbcUrl.isNullOrBlank()) { "DATABASE ability requires config.jdbcUrl" }
        require(!sql.isNullOrBlank()) { "DATABASE ability requires config.sql" }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RedisNodeConfig(
    val host: String? = "localhost",
    val port: Int? = 6379,
    val password: String? = null,
    val operation: String? = "GET",
    val key: String?,
    val value: Any? = null,
    val channel: String? = null
) {
    init {
        require(!operation.isNullOrBlank()) { "REDIS ability requires config.operation" }
        when (operation?.uppercase()) {
            "GET", "SET" -> require(!key.isNullOrBlank()) { "Redis ${operation.uppercase()} requires config.key" }
            "PUBLISH" -> require(!channel.isNullOrBlank()) { "Redis PUBLISH requires config.channel" }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ElasticsearchNodeConfig(
    val url: String?,
    val method: String? = "POST",
    val body: Any? = null,
    val username: String? = null,
    val password: String? = null
) {
    init {
        require(!url.isNullOrBlank()) { "ELASTICSEARCH ability requires config.url" }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KafkaNodeConfig(
    val bootstrapServers: String?,
    val topic: String?,
    val key: String? = null,
    val value: Any? = null
) {
    init {
        require(!bootstrapServers.isNullOrBlank()) { "KAFKA ability requires config.bootstrapServers" }
        require(!topic.isNullOrBlank()) { "KAFKA ability requires config.topic" }
    }
}

object NodeConfigValidator {
    fun validateNodeConfig(nodeType: NodeType, config: Map<String, Any?>) {
        if (nodeType == NodeType.ATOMIC_ABILITY) {
            val abilityType = config["abilityType"]?.toString()
            try {
                when (abilityType) {
                    "REST" -> nodeConfigMapper.convertValue(config, RestNodeConfig::class.java)
                    "DATABASE" -> nodeConfigMapper.convertValue(config, DatabaseNodeConfig::class.java)
                    "REDIS" -> nodeConfigMapper.convertValue(config, RedisNodeConfig::class.java)
                    "ELASTICSEARCH" -> nodeConfigMapper.convertValue(config, ElasticsearchNodeConfig::class.java)
                    "KAFKA" -> nodeConfigMapper.convertValue(config, KafkaNodeConfig::class.java)
                }
            } catch (e: Exception) {
                val causeMsg = if (e.cause is IllegalArgumentException) {
                    e.cause?.message
                } else {
                    e.message
                }
                throw AppException("Invalid configuration for ability $abilityType: $causeMsg")
            }
        }
    }
}
