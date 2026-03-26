plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":modules:flowforge-common"))
    implementation(project(":modules:flowforge-workflow"))
    implementation(project(":modules:flowforge-runtime"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-tx")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("redis.clients:jedis:5.1.3")
    implementation("org.apache.kafka:kafka-clients:3.8.0")
}
