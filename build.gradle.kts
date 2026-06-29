import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    // https://plugins.gradle.org/plugin/org.springframework.boot
    id("org.springframework.boot") version "4.1.0"
    // https://plugins.gradle.org/plugin/io.spring.dependency-management
    id("io.spring.dependency-management") version "1.1.7"
    // https://plugins.gradle.org/plugin/org.flywaydb.flyway
    id("org.flywaydb.flyway") version "12.9.0"
    // https://plugins.gradle.org/plugin/com.github.ben-manes.versions
    id("com.github.ben-manes.versions") version "0.54.0"
}

// Version Constants
val springBootVersion = "4.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

group = "dev.freitas"
version = "0.1.0"

repositories {
    mavenCentral()
    maven {
        name = "arbjergDevReleases"
        url = uri("https://maven.lavalink.dev/releases")
    }
}

dependencies {
    // Spring Boot: DI, configuration binding, blocking JDBC persistence. No webflux/r2dbc —
    // this is a single-player text game; Java 25 virtual threads make blocking JDBC safe under
    // concurrent command handling, so the reactive stack from ukulele is unnecessary here.
    implementation("org.springframework.boot:spring-boot-starter:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc:$springBootVersion")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    // https://github.com/discord-jda/JDA  — Discord gateway + slash commands (no audio module used).
    implementation("net.dv8tion:JDA:6.4.2") {
        exclude(module = "opus-java")
    }

    // https://mvnrepository.com/artifact/com.h2database/h2
    runtimeOnly("com.h2database:h2:2.4.240")
    // https://mvnrepository.com/artifact/org.flywaydb/flyway-core
    implementation("org.flywaydb:flyway-core:12.9.0")
    // Spring Boot 4 split autoconfigurations into per-feature modules; Flyway's lives here. Without
    // it, migrations silently never run and the schema is never created.
    implementation("org.springframework.boot:spring-boot-flyway:$springBootVersion")
    // https://github.com/ben-manes/caffeine — per-player save cache (mirrors ukulele's pattern).
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    // Used to (de)serialize game state to JSON blobs and to load authored content files.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.3")
}

tasks.withType<BootJar> {
    archiveFileName.set("delve.jar")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    doLast {
        // Copy the jar to the project root so the Dockerfile (and users) can find it easily.
        copy {
            from("build/libs/delve.jar")
            into(".")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    jvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
