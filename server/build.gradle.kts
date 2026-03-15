//import org.jooq.meta.kotlin.configuration
//import org.jooq.meta.kotlin.database
//import org.jooq.meta.kotlin.forcedTypes
//import org.jooq.meta.kotlin.generate
//import org.jooq.meta.kotlin.generator
//import org.jooq.meta.kotlin.jdbc
//import org.jooq.meta.kotlin.properties
//import org.jooq.meta.kotlin.property
//import org.jooq.meta.kotlin.target

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
    id("org.springframework.boot").version("4.0.0-RC2")
    id("org.jooq.jooq-codegen-gradle").version("3.19.27")
    id("org.flywaydb.flyway").version("11.16.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25;
    targetCompatibility = JavaVersion.VERSION_25;
}

sourceSets {
    main {
        java.srcDir("build/generated-src/jooq/main")
        java.srcDir("build/generated/sources/annotationProcessor/java")
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

    // https://mvnrepository.com/artifact/org.apache.commons/commons-compress
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")
    implementation("com.google.jimfs:jimfs:1.3.0")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    runtimeOnly("org.postgresql:postgresql:42.7.7")
    testRuntimeOnly("org.postgresql:postgresql:42.7.7")

    jooqCodegen("org.postgresql:postgresql:42.7.7")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.27.7")
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

tasks.test {
    useJUnitPlatform()
}