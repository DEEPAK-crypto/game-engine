plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.gatling.gradle") version "3.10.3"
}

dependencies {
    // Project dependencies
    implementation(project(":core"))
    implementation(project(":infrastructure"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Redis connection pooling (required for Lettuce pooling)
    implementation("org.apache.commons:commons-pool2")

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Structured logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // OpenAPI documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers:1.19.4")
    testImplementation("org.testcontainers:postgresql:1.19.4")
    testImplementation("org.testcontainers:cassandra:1.19.4")
    testImplementation("org.testcontainers:junit-jupiter:1.19.4")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("it.ozimov:embedded-redis:0.7.3") {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }

    // Gatling for load testing (Kotlin support)
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.10.3")
    gatling("io.gatling:gatling-app:3.10.3")
    gatling(kotlin("stdlib"))
}

tasks.bootJar {
    enabled = true
    archiveFileName.set("game-service.jar")
}

// Configure Gatling
gatling {
    logLevel = "WARN"

    // Optional: run only specific simulations
    // simulations = {
    //     include("com.gameplatform.game.gatling.GameLoadSimulation")
    // }
}