plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":modules:flowforge-common"))
    implementation(project(":modules:flowforge-workflow"))
    implementation(project(":modules:flowforge-runtime"))
    implementation("org.springframework.boot:spring-boot-starter")
}

