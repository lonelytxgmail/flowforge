package com.flowforge.engine

import com.fasterxml.jackson.databind.ObjectMapper
import com.flowforge.common.model.AppException
import com.flowforge.common.model.NodeType
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.DriverManager
import java.time.Duration
import java.util.Properties

enum class AtomicAbilityType {
    REST,
    REDIS,
    DATABASE,
    ELASTICSEARCH,
    KAFKA
}

interface AtomicAbilityHandler {
    fun supports(type: AtomicAbilityType): Boolean

    fun execute(context: NodeExecutionContext): NodeExecutionResult
}

@Component
class AtomicAbilityHandlerRegistry(
    handlers: List<AtomicAbilityHandler>
) {
    private val handlersByType = handlers.associateBy { handler ->
        AtomicAbilityType.entries.first { handler.supports(it) }
    }

    fun getHandler(type: AtomicAbilityType): AtomicAbilityHandler =
        handlersByType[type] ?: throw AppException("No AtomicAbilityHandler registered for type: $type")
}

@Component
class AtomicAbilityNodeExecutor(
    private val atomicAbilityHandlerRegistry: AtomicAbilityHandlerRegistry
) : NodeExecutor {
    override fun supports(nodeType: NodeType): Boolean = nodeType == NodeType.ATOMIC_ABILITY

    override fun execute(context: NodeExecutionContext): NodeExecutionResult {
        if (shouldUseMockAbility(context)) {
            return NodeExecutionResult(
                output = mapOf(
                    "handledBy" to "mock-atomic-ability",
                    "nodeId" to context.node.id,
                    "message" to "Atomic ability executed successfully",
                    "previousInput" to context.input
                )
            )
        }

        val abilityType = context.node.config["abilityType"]?.toString() ?: "REST"
        val handler = atomicAbilityHandlerRegistry.getHandler(AtomicAbilityType.valueOf(abilityType))
        
        // 全局动态上下文挂载与渲染 (拦截并替换 {{ context | steps }})
        val evaluatedConfig = ExpressionEvaluator.evaluate(context.node.config, context.workflowContext)
        val renderedContext = context.copy(node = context.node.copy(config = evaluatedConfig))

        return handler.execute(renderedContext)
    }

    private fun shouldUseMockAbility(context: NodeExecutionContext): Boolean =
        context.node.config["abilityType"] == null &&
            context.node.config["url"] == null &&
            context.node.config["jdbcUrl"] == null &&
            context.node.config["host"] == null &&
            context.node.config["bootstrapServers"] == null
}

@Component
class DigitalEmployeeNodeExecutor(
    private val restAtomicAbilityHandler: RestAtomicAbilityHandler
) : NodeExecutor {
    override fun supports(nodeType: NodeType): Boolean = nodeType == NodeType.DIGITAL_EMPLOYEE

    override fun execute(context: NodeExecutionContext): NodeExecutionResult {
        val translatedNode = context.node.copy(
            config = context.node.config + mapOf("abilityType" to "REST")
        )
        return restAtomicAbilityHandler.execute(
            context.copy(node = translatedNode)
        )
    }
}

@Component
class ConditionNodeExecutor : NodeExecutor {
    override fun supports(nodeType: NodeType): Boolean = nodeType == NodeType.CONDITION

    override fun execute(context: NodeExecutionContext): NodeExecutionResult =
        NodeExecutionResult(output = context.input)
}

@Component
class RestAtomicAbilityHandler(
    private val objectMapper: ObjectMapper
) : AtomicAbilityHandler {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun supports(type: AtomicAbilityType): Boolean = type == AtomicAbilityType.REST

    override fun execute(context: NodeExecutionContext): NodeExecutionResult {
        val authConfig = context.node.config["auth"] as? Map<*, *>
        val sessionContextKey = authConfig?.get("sessionContextKey")?.toString() ?: "authSession"
        val sessionContext = context.workflowContext[sessionContextKey] as? Map<*, *>

        val loginResult = if (authConfig?.get("type")?.toString() == "login_session" && sessionContext == null) {
            performLogin(authConfig, context, sessionContextKey)
        } else {
            LoginResult(emptyMap(), emptyMap())
        }

        val requestContext = context.workflowContext + loginResult.contextUpdates
        val request = buildRequest(context, requestContext, authConfig)
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw AppException("REST ability failed with status ${response.statusCode()}: ${response.body()}")
        }

        val output = mutableMapOf<String, Any?>(
            "abilityType" to "REST",
            "statusCode" to response.statusCode()
        )

        val streaming = context.node.config["streaming"]?.toString()?.toBoolean() ?: false
        if (streaming) {
            output["streaming"] = true
            output["chunks"] = response.body().lines().toList()
            output["rawBody"] = response.body()
        } else {
            output["streaming"] = false
            output["body"] = response.body()
        }

        if (loginResult.metadata.isNotEmpty()) {
            output["auth"] = loginResult.metadata
        }

        return NodeExecutionResult(
            output = output,
            contextUpdates = loginResult.contextUpdates
        )
    }

    private fun performLogin(
        authConfig: Map<*, *>,
        context: NodeExecutionContext,
        sessionContextKey: String
    ): LoginResult {
        val loginUrl = authConfig["loginUrl"]?.toString()
            ?: throw AppException("REST login_session requires auth.loginUrl")
        val loginMethod = authConfig["loginMethod"]?.toString()?.uppercase() ?: "POST"
        val loginBody = authConfig["loginBody"] ?: context.input
        val tokenPath = authConfig["tokenPath"]?.toString() ?: "token"
        val headerName = authConfig["headerName"]?.toString() ?: "Authorization"
        val prefix = authConfig["prefix"]?.toString() ?: "Bearer"

        val loginRequest = HttpRequest.newBuilder()
            .uri(URI.create(loginUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .method(loginMethod, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(loginBody)))
            .build()

        val loginResponse = httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString())
        if (loginResponse.statusCode() >= 400) {
            throw AppException("REST login_session failed with status ${loginResponse.statusCode()}")
        }

        val responseMap = parseJsonObject(loginResponse.body())
        val token = readPath(responseMap, tokenPath)?.toString()
            ?: throw AppException("Cannot extract auth token from path: $tokenPath")

        val session = mapOf(
            "token" to token,
            "headerName" to headerName,
            "prefix" to prefix
        )

        return LoginResult(
            contextUpdates = mapOf(sessionContextKey to session),
            metadata = mapOf("sessionContextKey" to sessionContextKey, "headerName" to headerName)
        )
    }

    private fun buildRequest(
        context: NodeExecutionContext,
        workflowContext: Map<String, Any?>,
        authConfig: Map<*, *>?
    ): HttpRequest {
        val url = context.node.config["url"]?.toString()
            ?: throw AppException("REST ability requires config.url")
        val method = context.node.config["method"]?.toString()?.uppercase() ?: "POST"
        val headers = mutableMapOf<String, String>()
        ((context.node.config["headers"] as? Map<*, *>) ?: emptyMap<Any, Any>()).forEach { (key, value) ->
            headers[key.toString()] = value?.toString() ?: ""
        }

        applyAuthHeaders(headers, authConfig, workflowContext)

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))

        headers.forEach { (key, value) -> requestBuilder.header(key, value) }

        return if (method == "GET") {
            requestBuilder.GET().build()
        } else {
            val requestBody = context.node.config["body"] ?: context.input
            requestBuilder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build()
        }
    }

    private fun applyAuthHeaders(
        headers: MutableMap<String, String>,
        authConfig: Map<*, *>?,
        workflowContext: Map<String, Any?>
    ) {
        if (authConfig == null) {
            return
        }

        when (authConfig["type"]?.toString()) {
            "bearer_static" -> {
                val token = authConfig["token"]?.toString()
                    ?: throw AppException("auth.token is required for bearer_static")
                headers["Authorization"] = "Bearer $token"
            }
            "basic" -> {
                val username = authConfig["username"]?.toString() ?: ""
                val password = authConfig["password"]?.toString() ?: ""
                val encoded = java.util.Base64.getEncoder()
                    .encodeToString("$username:$password".toByteArray())
                headers["Authorization"] = "Basic $encoded"
            }
            "login_session" -> {
                val sessionContextKey = authConfig["sessionContextKey"]?.toString() ?: "authSession"
                val session = workflowContext[sessionContextKey] as? Map<*, *> ?: return
                val headerName = session["headerName"]?.toString() ?: "Authorization"
                val prefix = session["prefix"]?.toString() ?: "Bearer"
                val token = session["token"]?.toString()
                    ?: throw AppException("Stored auth session is missing token")
                headers[headerName] = "$prefix $token"
            }
        }
    }

    private fun parseJsonObject(body: String): Map<String, Any?> {
        if (body.isBlank()) {
            return emptyMap()
        }
        return objectMapper.readValue(body, objectMapper.typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java))
            as Map<String, Any?>
    }

    private fun readPath(input: Map<String, Any?>, path: String): Any? {
        var current: Any? = input
        for (segment in path.split(".")) {
            current = when (current) {
                is Map<*, *> -> current[segment]
                else -> return null
            }
        }
        return current
    }

    private data class LoginResult(
        val contextUpdates: Map<String, Any?>,
        val metadata: Map<String, Any?>
    )
}

@Component
class RedisAtomicAbilityHandler(
    private val objectMapper: ObjectMapper
) : AtomicAbilityHandler {
    override fun supports(type: AtomicAbilityType): Boolean = type == AtomicAbilityType.REDIS

    override fun execute(context: NodeExecutionContext): NodeExecutionResult {
        val host = context.node.config["host"]?.toString() ?: "localhost"
        val port = context.node.config["port"]?.toString()?.toIntOrNull() ?: 6379
        val password = context.node.config["password"]?.toString()
        val operation = context.node.config["operation"]?.toString()?.uppercase() ?: "GET"
        val key = context.node.config["key"]?.toString()

        Jedis(host, port).use { jedis ->
            if (!password.isNullOrBlank()) {
                jedis.auth(password)
            }

            val output = when (operation) {
                "GET" -> {
                    requireNotNull(key) { "Redis GET requires config.key" }
                    mapOf("abilityType" to "REDIS", "operation" to operation, "value" to jedis.get(key))
                }
                "SET" -> {
                    requireNotNull(key) { "Redis SET requires config.key" }
                    val value = context.node.config["value"]?.toString()
                        ?: objectMapper.writeValueAsString(context.input)
                    jedis.set(key, value)
                    mapOf("abilityType" to "REDIS", "operation" to operation, "result" to "OK")
                }
                "PUBLISH" -> {
                    val channel = context.node.config["channel"]?.toString()
                        ?: throw AppException("Redis PUBLISH requires config.channel")
                    val value = context.node.config["value"]?.toString()
                        ?: objectMapper.writeValueAsString(context.input)
                    val receivers = jedis.publish(channel, value)
                    mapOf("abilityType" to "REDIS", "operation" to operation, "receivers" to receivers)
                }
                else -> throw AppException("Unsupported Redis operation: $operation")
            }
            return NodeExecutionResult(output = output)
        }
    }
}

@Component
class DatabaseAtomicAbilityHandler : AtomicAbilityHandler {
    override fun supports(type: AtomicAbilityType): Boolean = type == AtomicAbilityType.DATABASE

    override fun execute(context: NodeExecutionContext): NodeExecutionResult {
        val jdbcUrl = context.node.config["jdbcUrl"]?.toString()
            ?: throw AppException("DATABASE ability requires config.jdbcUrl")
        val username = context.node.config["username"]?.toString()
        val password = context.node.config["password"]?.toString()
        val sql = context.node.config["sql"]?.toString()
            ?: throw AppException("DATABASE ability requires config.sql")
        val operation = context.node.config["operation"]?.toString()?.uppercase() ?: "QUERY"

        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                val output = when (operation) {
                    "QUERY" -> {
                        val resultSet = statement.executeQuery()
                        val rows = mutableListOf<Map<String, Any?>>()
                        val metaData = resultSet.metaData
                        while (resultSet.next()) {
                            val row = mutableMapOf<String, Any?>()
                            for (index in 1..metaData.columnCount) {
                                row[metaData.getColumnLabel(index)] = resultSet.getObject(index)
                            }
                            rows += row
                        }
                        mapOf("abilityType" to "DATABASE", "operation" to operation, "rows" to rows)
                    }
                    "UPDATE" -> {
                        val updatedRows = statement.executeUpdate()
                        mapOf("abilityType" to "DATABASE", "operation" to operation, "updatedRows" to updatedRows)
                    }
                    else -> throw AppException("Unsupported DATABASE operation: $operation")
                }
                return NodeExecutionResult(output = output)
            }
        }
    }
}

@Component
class ElasticsearchAtomicAbilityHandler(
    private val objectMapper: ObjectMapper
) : AtomicAbilityHandler {
    private val httpClient = HttpClient.newHttpClient()

    override fun supports(type: AtomicAbilityType): Boolean = type == AtomicAbilityType.ELASTICSEARCH

    override fun execute(context: NodeExecutionContext): NodeExecutionResult {
        val url = context.node.config["url"]?.toString()
            ?: throw AppException("ELASTICSEARCH ability requires config.url")
        val method = context.node.config["method"]?.toString()?.uppercase() ?: "POST"
        val body = context.node.config["body"] ?: context.input
        val username = context.node.config["username"]?.toString()
        val password = context.node.config["password"]?.toString()

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))

        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            val encoded = java.util.Base64.getEncoder()
                .encodeToString("$username:$password".toByteArray())
            requestBuilder.header("Authorization", "Basic $encoded")
        }

        val request = requestBuilder.build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw AppException("ELASTICSEARCH ability failed with status ${response.statusCode()}: ${response.body()}")
        }
        return NodeExecutionResult(
            output = mapOf(
                "abilityType" to "ELASTICSEARCH",
                "statusCode" to response.statusCode(),
                "body" to response.body()
            )
        )
    }
}

@Component
class KafkaAtomicAbilityHandler(
    private val objectMapper: ObjectMapper
) : AtomicAbilityHandler {
    override fun supports(type: AtomicAbilityType): Boolean = type == AtomicAbilityType.KAFKA

    override fun execute(context: NodeExecutionContext): NodeExecutionResult {
        val bootstrapServers = context.node.config["bootstrapServers"]?.toString()
            ?: throw AppException("KAFKA ability requires config.bootstrapServers")
        val topic = context.node.config["topic"]?.toString()
            ?: throw AppException("KAFKA ability requires config.topic")
        val key = context.node.config["key"]?.toString()
        val value = context.node.config["value"] ?: context.input

        val properties = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        }

        KafkaProducer<String, String>(properties).use { producer ->
            val metadata = producer.send(
                ProducerRecord(topic, key, objectMapper.writeValueAsString(value))
            ).get()

            return NodeExecutionResult(
                output = mapOf(
                    "abilityType" to "KAFKA",
                    "topic" to metadata.topic(),
                    "partition" to metadata.partition(),
                    "offset" to metadata.offset()
                )
            )
        }
    }
}
