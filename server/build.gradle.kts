import org.jooq.meta.kotlin.*

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
    id("nu.studer.jooq").version("10.1.1")
    id("org.flywaydb.flyway").version("11.16.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25;
    targetCompatibility = JavaVersion.VERSION_25;
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

    runtimeOnly("org.postgresql:postgresql:42.7.7")

    jooqGenerator("org.postgresql:postgresql:42.7.7")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.27.6")
}

jooq {
    version.set("3.19.27")
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        create("main") {  // name of the jOOQ configuration
            generateSchemaSourceOnCompilation.set(true)  // default (can be omitted)

            jooqConfiguration {
                logging = org.jooq.meta.jaxb.Logging.WARN
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
                generator {
                    name = "org.jooq.codegen.DefaultGenerator"
                    database {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "sona"
                        excludes = "flyway_schema_history"
                        forcedTypes {
//                            forcedType {
//                                name = "varchar"
//                                includeExpression = ".*"
//                                includeTypes = "JSONB?"
//                            }
//                            forcedType {
//                                name = "varchar"
//                                includeExpression = ".*"
//                                includeTypes = "INET"
//                            }
                        }
                    }
                    generate {
                        isDeprecated = false
                        isPojosAsJavaRecordClasses = true
                        isPojos = true
                        isPojosEqualsAndHashCode = false
                        isPojosToString = false
                        isSerializablePojos = false
                        isFluentSetters = true
                        isDaos = true
                        isSpringAnnotations = true
                        isSpringDao = true
                    }
                    target {
                        packageName = "org.sona.model"
                        directory = "build/generated-src/jooq/main"  // default (can be omitted)
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

tasks.named<nu.studer.gradle.jooq.JooqGenerate>("generateJooq") {
    // ensure database schema has been prepared by Flyway before generating the jOOQ sources
    dependsOn("flywayMigrate")
    allInputsDeclared.set(true)
    outputs.upToDateWhen{ false }

    (launcher::set)(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}

tasks.test {
    useJUnitPlatform()
}