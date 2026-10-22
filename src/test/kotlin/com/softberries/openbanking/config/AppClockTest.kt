package com.softberries.openbanking.config

import com.softberries.openbanking.support.withEnv
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AppClockTest {
    private val instant = Instant.parse("2026-10-21T22:30:00Z")
    private val fixed = Clock.fixed(instant, ZoneOffset.UTC)

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties::class)
    @Import(CoreConfiguration::class)
    class Config

    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration::class.java))
            .withUserConfiguration(Config::class.java)

    @Test
    fun `today is evaluated in Europe Warsaw`() {
        val clock = AppClock(ZoneId.of("Europe/Warsaw"), fixed)

        clock.now() shouldBe instant
        clock.today() shouldBe LocalDate.parse("2026-10-22")
    }

    @Test
    fun `today is evaluated in UTC`() {
        AppClock(ZoneId.of("UTC"), fixed).today() shouldBe LocalDate.parse("2026-10-21")
    }

    @Test
    fun `APP_TIME_ZONE drives the AppClock bean, default Europe Warsaw`() {
        runner.run { it.getBean(AppClock::class.java).zone shouldBe ZoneId.of("Europe/Warsaw") }
        runner.withEnv("APP_TIME_ZONE" to "UTC").run { it.getBean(AppClock::class.java).zone shouldBe ZoneId.of("UTC") }
    }

    @Test
    fun `an invalid APP_TIME_ZONE fails startup with a message naming the variable`() {
        runner.withEnv("APP_TIME_ZONE" to "Mars/Olympus").run { context ->
            val failure = context.startupFailure
            generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() } shouldContain
                "APP_TIME_ZONE 'Mars/Olympus' is not a valid time zone id"
        }
    }
}
