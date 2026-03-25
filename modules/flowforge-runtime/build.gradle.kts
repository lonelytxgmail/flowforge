plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":modules:flowforge-common"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}

