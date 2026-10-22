import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.jte)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependency.check)
    `java-test-fixtures`
}

group = "com.softberries.openbanking"
version =
    providers
        .fileContents(layout.projectDirectory.file("VERSION"))
        .asText
        .map { it.trim().removePrefix("v") }
        .orElse("dev")
        .get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.opentelemetry.instrumentation.bom))
    testFixturesImplementation(platform(libs.spring.boot.bom))
    annotationProcessor(platform(libs.spring.boot.bom))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    annotationProcessor(libs.spring.boot.configuration.processor)

    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    implementation(libs.jte)
    implementation(libs.jte.kotlin)
    implementation(libs.jte.spring.boot.starter)

    implementation(libs.jobrunr.spring.boot.starter)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    implementation(libs.nimbus.jose.jwt)
    implementation(libs.bouncycastle.bcpkix)

    implementation(libs.logstash.logback.encoder)
    implementation(libs.opentelemetry.spring.boot.starter)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.logback.appender)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.mockito")
    }
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.wiremock)
    testImplementation(libs.playwright)
    testImplementation(libs.kotlin.faker)
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesImplementation(libs.kotlin.faker)
}

jte {
    sourceDirectory =
        layout.projectDirectory
            .dir("src/main/jte")
            .asFile
            .toPath()
    contentType = gg.jte.ContentType.Html
    generate()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "1g"
    systemProperty("user.timezone", "UTC")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

ktlint {
    version =
        libs.versions.ktlint.cli
            .get()
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

// The JTE generator adds build/generated-sources/jte to the main source set, which ktlint scans (and then skips).
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    dependsOn("generateJte")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin", "src/testFixtures/kotlin")
}

// detekt 1.23 runs on its own embedded Kotlin compiler; keep the Kotlin plugin from upgrading it.
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(
                libs.versions.detekt.kotlin
                    .get(),
            )
        }
    }
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required = true
        xml.required = false
        sarif.required = false
        md.required = false
    }
}

kover {
    reports {
        filters {
            excludes {
                classes("gg.jte.generated.*", "*Application*")
            }
        }
    }
}

tasks.check {
    dependsOn("koverXmlReport", "koverVerify")
}

dependencyCheck {
    failBuildOnCVSS = 9.0f
    suppressionFile = "config/dependency-check-suppressions.xml"
}

// Tailwind v4 standalone CLI (same version as tailwindcss-ruby 4.2.2 in the Ruby Gemfile.lock).
// Declared here so no later unit edits the build; M-003 wires it into processResources.
val tailwindVersion = libs.versions.tailwind.get()
val tailwindBinary = layout.buildDirectory.file("tailwind/tailwindcss-$tailwindVersion")
tasks.register("downloadTailwind") {
    group = "build setup"
    description = "Downloads the Tailwind CSS v4 standalone CLI for the current platform."
    outputs.file(tailwindBinary)
    doLast {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val platform =
            when {
                os.contains("mac") -> "macos"
                os.contains("win") -> "windows"
                else -> "linux"
            }
        val cpu = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x64"
        val suffix = if (platform == "windows") ".exe" else ""
        val url = "https://github.com/tailwindlabs/tailwindcss/releases/download/v$tailwindVersion/tailwindcss-$platform-$cpu$suffix"
        val target = tailwindBinary.get().asFile
        target.parentFile.mkdirs()
        uri(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        target.setExecutable(true)
    }
}
