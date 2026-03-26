plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":modules:flowforge-common"))
    implementation(project(":modules:flowforge-workflow"))
    implementation(project(":modules:flowforge-runtime"))
    implementation(project(":modules:flowforge-engine"))
    implementation(project(":modules:flowforge-api"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")
}

tasks.bootJar {
    archiveFileName.set("flowforge-app.jar")
}
