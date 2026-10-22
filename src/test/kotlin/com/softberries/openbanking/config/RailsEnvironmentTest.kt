package com.softberries.openbanking.config

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment

/** ENV contract of config/database.yml, config/puma.rb and production.rb, resolved through application*.yml. */
class RailsEnvironmentTest {
    @Configuration(proxyBeanMethods = false)
    class Empty

    private fun environment(vararg properties: String): Environment {
        val context =
            SpringApplicationBuilder(Empty::class.java)
                .web(WebApplicationType.NONE)
                .properties(*properties)
                .run()
        return context.use { snapshot(it.environment) }
    }

    private fun snapshot(env: ConfigurableEnvironment): Environment {
        val keys =
            listOf(
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "spring.datasource.hikari.maximum-pool-size",
                "server.port",
                "logging.level.root",
                "spring.jpa.properties.hibernate.jdbc.time_zone",
                "spring.jpa.hibernate.ddl-auto",
                "spring.flyway.baseline-on-migrate",
                "spring.flyway.baseline-version",
                "spring.flyway.out-of-order",
            )
        return MockEnvironment().apply {
            keys.forEach { key -> env.getProperty(key)?.let { setProperty(key, it) } }
            setActiveProfiles(*env.activeProfiles)
        }
    }

    @Test
    fun `without DATABASE_URL development uses the POSTGRES_ defaults`() {
        val env = environment()

        env.activeProfiles.toList() shouldBe listOf("development")
        env.getProperty("spring.datasource.url") shouldBe "jdbc:postgresql://localhost:5432/open_banking_rails_development"
        env.getProperty("spring.datasource.username") shouldBe "postgres"
        env.getProperty("spring.datasource.password") shouldBe "postgres"
        env.getProperty("spring.flyway.out-of-order") shouldBe "true"
    }

    @Test
    fun `POSTGRES_ variables configure the datasource`() {
        val env =
            environment(
                "POSTGRES_HOST=pg.internal",
                "POSTGRES_PORT=6543",
                "POSTGRES_USER=alice",
                "POSTGRES_PASSWORD=secret",
                "POSTGRES_DB=ledger",
            )

        env.getProperty("spring.datasource.url") shouldBe "jdbc:postgresql://pg.internal:6543/ledger"
        env.getProperty("spring.datasource.username") shouldBe "alice"
        env.getProperty("spring.datasource.password") shouldBe "secret"
    }

    @Test
    fun `RAILS_ENV=test selects the test profile and its fixed database name`() {
        val env = environment("RAILS_ENV=test")

        env.activeProfiles.toList() shouldBe listOf("test")
        env.getProperty("spring.datasource.url") shouldBe "jdbc:postgresql://localhost:5432/open_banking_rails_test"
    }

    @Test
    fun `RAILS_ENV=production selects the production profile`() {
        val env = environment("RAILS_ENV=production")

        env.activeProfiles.toList() shouldBe listOf("production")
        env.getProperty("spring.flyway.out-of-order") shouldBe "false"
    }

    @Test
    fun `an explicit Spring profile wins over RAILS_ENV`() {
        val env = environment("RAILS_ENV=production", "spring.profiles.active=test")

        env.activeProfiles.toList() shouldBe listOf("test")
    }

    @Test
    fun `PORT, RAILS_MAX_THREADS and RAILS_LOG_LEVEL defaults`() {
        val env = environment()

        env.getProperty("server.port") shouldBe "3000"
        env.getProperty("spring.datasource.hikari.maximum-pool-size") shouldBe "5"
        env.getProperty("logging.level.root") shouldBe "info"
    }

    @Test
    fun `PORT, RAILS_MAX_THREADS and RAILS_LOG_LEVEL are honoured`() {
        val env = environment("PORT=8080", "RAILS_MAX_THREADS=12", "RAILS_LOG_LEVEL=debug")

        env.getProperty("server.port") shouldBe "8080"
        env.getProperty("spring.datasource.hikari.maximum-pool-size") shouldBe "12"
        env.getProperty("logging.level.root") shouldBe "debug"
    }

    @Test
    fun `DATABASE_URL wins over POSTGRES_ variables`() {
        val env = environment("DATABASE_URL=postgres://obr:obr@db:5432/obr_production", "POSTGRES_HOST=ignored")

        env.getProperty("spring.datasource.url") shouldBe "jdbc:postgresql://db:5432/obr_production"
        env.getProperty("spring.datasource.username") shouldBe "obr"
    }

    @Test
    fun `Hibernate validates the schema and writes JDBC timestamps in UTC, Flyway baselines existing databases`() {
        val env = environment()

        env.getProperty("spring.jpa.hibernate.ddl-auto") shouldBe "validate"
        env.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone") shouldBe "UTC"
        env.getProperty("spring.flyway.baseline-on-migrate") shouldBe "true"
        env.getProperty("spring.flyway.baseline-version") shouldBe "1"
    }

    @Test
    fun `post processor leaves an explicit profile alone`() {
        val env = MockEnvironment().withProperty("RAILS_ENV", "production")
        env.setActiveProfiles("custom")

        RailsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())

        env.propertySources.contains(RailsEnvironmentPostProcessor.RAILS_ENV_SOURCE) shouldBe false
    }
}
