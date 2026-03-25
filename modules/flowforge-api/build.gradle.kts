plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":modules:flowforge-common"))
    implementation(project(":modules:flowforge-workflow"))
    implementation(project(":modules:flowforge-runtime"))
    implementation(project(":modules:flowforge-engine"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.22")
}
