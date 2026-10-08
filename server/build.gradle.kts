buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Add the Flyway PostgreSQL database module to the plugin's classpath
        classpath("org.flywaydb:flyway-database-postgresql:11.16.0")

        // Add the PostgreSQL JDBC driver to the plugin's classpath
        classpath("org.postgresql:postgresql:42.7.7")
    }
}

plugins {
    id("java")
    id("org.springframework.boot").version("4.1.1")
    id("org.jooq.jooq-codegen-gradle").version("3.19.27")
    id("org.flywaydb.flyway").version("11.16.0")
}

sourceSets {
    main {
        java.srcDir("build/generated-src/jooq/main")
    }
}

apply(plugin = "io.spring.dependency-management")

group = "org.sona"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

flyway {
    url = "jdbc:postgresql://localhost:5432/sona"
    user = "postgres"
    password = "12345"
    defaultSchema = "sona"
    placeholders = mapOf(
        "schema" to "sona",
        "user" to "sona"
    )
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // https://mvnrepository.com/artifact/org.apache.commons/commons-compress
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("dev.javax:bitstream:0.1.0-RC")
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

    runtimeOnly("org.postgresql:postgresql:42.7.7")
    testRuntimeOnly("org.postgresql:postgresql:42.7.7")

    jooqCodegen("org.postgresql:postgresql:42.7.7")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testImplementation("com.google.jimfs:jimfs:1.3.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")

    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.27.6")
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://localhost:5432/sona"
            user = "postgres"
            password = "12345"
            properties {
                property {
                    key = "ssl"
                    value = "false"
                }
            }
        }
        logging = org.jooq.meta.jaxb.Logging.TRACE
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "sona"
                includes = ".*"
                excludes = "flyway_schema_history"
            }
            generate {
                deprecated = false
                pojosAsJavaRecordClasses = true
                pojos = true
                immutablePojos = true
                pojosEqualsAndHashCode = false
                interfaces = true
            }
            target {
                packageName = "org.sona.model"
                directory = "build/generated-src/jooq/main"  // default (can be omitted)
            }
        }
    }
}

tasks.named("jooqCodegen") {
    dependsOn("flywayMigrate")
}

// Code generation needs the database running, so it's run explicitly rather than on every compile.
tasks.named("compileJava") {
    mustRunAfter("jooqCodegen")
}

tasks.test {
    useJUnitPlatform()
}