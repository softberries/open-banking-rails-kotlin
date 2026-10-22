package com.softberries.openbanking.db

import com.softberries.openbanking.OpenBankingApplication
import com.softberries.openbanking.support.IntegrationTest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.DriverManager

/** A database created by the Rails app boots via `baselineOnMigrate` (D-005, U-107): V1 is recorded, nothing is re-applied. */
class RailsDbBaselineOnMigrateTest {
    private val databaseName = "rails_existing_db"

    @Test
    fun `boots on a Rails-created database without re-applying the baseline`() {
        val pg = IntegrationTest.postgres
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { connection ->
            connection.createStatement().use {
                it.execute("DROP DATABASE IF EXISTS $databaseName")
                it.execute("CREATE DATABASE $databaseName")
            }
        }
        val url = pg.jdbcUrl.replace("/${pg.databaseName}", "/$databaseName")
        val railsDb = JdbcTemplate(DriverManagerDataSource(url, pg.username, pg.password))
        railsDb.execute(ClassPathResource("rails-db/rails_schema_cd3bf60.sql").getContentAsString(Charsets.UTF_8))
        val tablesBefore = tables(railsDb)

        SpringApplicationBuilder(OpenBankingApplication::class.java)
            .web(WebApplicationType.NONE)
            .profiles("production")
            .run(
                "--spring.datasource.url=$url",
                "--spring.datasource.username=${pg.username}",
                "--spring.datasource.password=${pg.password}",
                "--app.secret-key-base=test",
                "--otel.sdk.disabled=true",
            ).close()

        val history = railsDb.queryForList("SELECT version, type FROM flyway_schema_history WHERE version IS NOT NULL")
        history.map { it["version"] to it["type"] } shouldBe listOf("1" to "BASELINE")
        railsDb.queryForObject("SELECT count(*) FROM schema_migrations", Int::class.java) shouldBe 33
        railsDb.queryForList(
            "SELECT tablename FROM pg_tables WHERE tablename LIKE 'gutentag%'",
            String::class.java,
        ) shouldContainExactlyInAnyOrder
            listOf("gutentag_tags", "gutentag_taggings")
        tables(railsDb).filterNot { it == "flyway_schema_history" || it.startsWith("jobrunr") } shouldContainExactlyInAnyOrder tablesBefore
    }

    private fun tables(db: JdbcTemplate): List<String> =
        db.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String::class.java)
}
