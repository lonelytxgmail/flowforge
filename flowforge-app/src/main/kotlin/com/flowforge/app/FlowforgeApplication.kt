package com.flowforge.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 整个系统的启动入口。
 *
 * Kotlin 里 `fun main()` 就是程序主函数。
 * `runApplication` 会启动 Spring Boot 容器，并自动扫描 `com.flowforge` 下的 Bean。
 */
@SpringBootApplication(scanBasePackages = ["com.flowforge"])
class FlowforgeApplication

fun main(args: Array<String>) {
    runApplication<FlowforgeApplication>(*args)
}

