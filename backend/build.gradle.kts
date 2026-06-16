import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.flywaydb.flyway") version "10.10.0"
    id("io.freefair.lombok") version "8.10"
}

group = "com.hms"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    // ── Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // ── Database — PostgreSQL replaces MySQL entirely
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // ── JPA / Hibernate extras
    // hypersistence-utils: @Type(JsonType.class) maps Java objects to PostgreSQL JSONB
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.4")

    // ── Connection pool (HikariCP — Spring Boot default)
    implementation("com.zaxxer:HikariCP")

    // ── Caching
    implementation("com.github.ben-manes.caffeine:caffeine")

    // ── Serialisation
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ── DTO mapping
    implementation("org.mapstruct:mapstruct:1.6.2")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.2")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")


    // ── Reports (Flying Saucer + OpenPDF)
    implementation("org.xhtmlrenderer:flying-saucer-pdf-openpdf:9.1.22")

    // ── SMS
    implementation("com.twilio.sdk:twilio:10.4.1")

    // ── API documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // ── Security
    implementation("org.springframework.security:spring-security-crypto")

    // ── Dev tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ── Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("hms-backend.jar")
}

flyway {
    url      = "jdbc:postgresql://${System.getenv("DB_HOST") ?: "localhost"}:${System.getenv("DB_PORT") ?: "5432"}/${System.getenv("DB_NAME") ?: "hms_db"}"
    user     = System.getenv("DB_USER") ?: "hms_user"
    password = System.getenv("DB_PASSWORD") ?: "hms_pass"
    locations = arrayOf("classpath:db/migration")
    mixed    = false
}
