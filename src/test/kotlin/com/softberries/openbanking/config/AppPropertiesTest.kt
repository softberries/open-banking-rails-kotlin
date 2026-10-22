package com.softberries.openbanking.config

import com.softberries.openbanking.support.withEnv
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.nio.file.Files
import java.nio.file.Path

class AppPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties::class)
    @Import(ProductionSecretsCheck::class)
    class Config

    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration::class.java))
            .withUserConfiguration(Config::class.java)

    @Test
    fun `version is the VERSION file without a leading v`(
        @TempDir dir: Path,
    ) {
        val file = Files.writeString(dir.resolve("VERSION"), "v1.4.2\n")

        AppProperties(versionFile = file.toString()).version shouldBe "1.4.2"
    }

    @Test
    fun `version keeps tags without a v prefix`(
        @TempDir dir: Path,
    ) {
        val file = Files.writeString(dir.resolve("VERSION"), "2026.10.1")

        AppProperties(versionFile = file.toString()).version shouldBe "2026.10.1"
    }

    @Test
    fun `version falls back to dev when there is no VERSION file`(
        @TempDir dir: Path,
    ) {
        AppProperties(versionFile = dir.resolve("missing").toString()).version shouldBe "dev"
    }

    @Test
    fun `edition is Personal`() {
        AppProperties().edition shouldBe "Personal"
    }

    @Test
    fun `binds the Ruby ENV names`() {
        runner
            .withEnv(
                "APP_HOST" to "finance.example.com",
                "APP_HOSTS" to "a.example.com, b.example.com",
                "APP_PROTOCOL" to "http",
                "APP_TIME_ZONE" to "UTC",
            ).withPropertyValues(
                "app.force-ssl=false",
                "app.secret-key-base=abc",
            ).run { context ->
                val props = context.getBean(AppProperties::class.java)
                props.host shouldBe "finance.example.com"
                props.allowedHosts shouldContainExactly listOf("a.example.com", "b.example.com")
                props.protocol shouldBe "http"
                props.zone.id shouldBe "UTC"
                props.forceSslEnabled shouldBe false
                props.secretKeyBase shouldBe "abc"
            }
    }

    @Test
    fun `Ruby defaults`() {
        val props = AppProperties()

        props.protocol shouldBe "https"
        props.forceSslEnabled shouldBe true
        props.publicHost shouldBe "localhost:3000"
        props.zone.id shouldBe "Europe/Warsaw"
        props.allowedHosts.shouldBeEmpty()
    }

    @Test
    fun `allowed hosts fall back to APP_HOST only when APP_HOSTS is unset`() {
        AppProperties(host = "finance.example.com").allowedHosts shouldContainExactly listOf("finance.example.com")
        AppProperties(host = "finance.example.com", hosts = "").allowedHosts.shouldBeEmpty()
    }

    @Test
    fun `FORCE_SSL is on only for the exact string true`() {
        AppProperties(forceSsl = "TRUE").forceSslEnabled shouldBe false
        AppProperties(forceSsl = "1").forceSslEnabled shouldBe false
    }

    @Test
    fun `production without SECRET_KEY_BASE fails at boot`() {
        runner.withPropertyValues("spring.profiles.active=production").run { context ->
            val failure = context.startupFailure.shouldNotBeNull()
            generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() } shouldContain
                "SECRET_KEY_BASE must be set in production"
        }
    }

    @Test
    fun `production with SECRET_KEY_BASE boots`() {
        runner.withPropertyValues("spring.profiles.active=production", "app.secret-key-base=s3cr3t").run { context ->
            context.startupFailure shouldBe null
        }
    }
}
