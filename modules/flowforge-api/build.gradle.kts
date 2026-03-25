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
}

