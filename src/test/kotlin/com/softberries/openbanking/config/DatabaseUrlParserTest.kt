package com.softberries.openbanking.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.mock.env.MockEnvironment

class DatabaseUrlParserTest {
    @Test
    fun `maps the Rails compose DATABASE_URL to JDBC settings`() {
        DatabaseUrl.parse("postgres://obr:obr@db:5432/obr_production") shouldBe
            JdbcSettings("jdbc:postgresql://db:5432/obr_production", "obr", "obr")
    }

    @Test
    fun `accepts postgresql scheme, default port, query parameters and encoded credentials`() {
        DatabaseUrl.parse("postgresql://us%40er:p%3Ass%2Fw@example.org/app?sslmode=require") shouldBe
            JdbcSettings("jdbc:postgresql://example.org:5432/app?sslmode=require", "us@er", "p:ss/w")
    }

    @Test
    fun `URL without credentials leaves user and password to the defaults`() {
        DatabaseUrl.parse("postgres://localhost/dev") shouldBe JdbcSettings("jdbc:postgresql://localhost:5432/dev", null, null)
    }

    @Test
    fun `JDBC URLs pass through`() {
        DatabaseUrl.parse("jdbc:postgresql://h:1/d") shouldBe JdbcSettings("jdbc:postgresql://h:1/d", null, null)
    }

    @Test
    fun `rejects other schemes`() {
        shouldThrow<IllegalArgumentException> { DatabaseUrl.parse("mysql://u:p@h/d") }
    }

    @Test
    fun `DATABASE_URL overrides the datasource properties`() {
        val env = MockEnvironment().withProperty("DATABASE_URL", "postgres://obr:obr@db:5432/obr_production")

        RailsEnvironmentPostProcessor().postProcessEnvironment(env, SpringApplication())

        env.getProperty("spring.datasource.url") shouldBe "jdbc:postgresql://db:5432/obr_production"
        env.getProperty("spring.datasource.username") shouldBe "obr"
        env.getProperty("spring.datasource.password") shouldBe "obr"
    }
}
