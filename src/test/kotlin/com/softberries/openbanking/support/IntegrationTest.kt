package com.softberries.openbanking.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base class for integration tests: one `postgres:17` container per JVM, migrated once by Flyway when the (cached)
 * Spring context starts, and every table except `flyway_schema_history` truncated before each test.
 *
 * Defaults `APP_TIME_ZONE=UTC` and `OTEL_SDK_DISABLED=true`; override them with `@TestPropertySource` on the subclass.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = ["app.time-zone=UTC", "otel.sdk.disabled=true"])
abstract class IntegrationTest {
    @Autowired
    protected lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun truncateTables() {
        val tables =
            jdbc.queryForList(
                "SELECT quote_ident(tablename) FROM pg_tables WHERE schemaname = 'public' AND tablename NOT IN (?, ?)",
                String::class.java,
                FLYWAY_HISTORY,
                JOBRUNR_MIGRATIONS,
            )
        if (tables.isNotEmpty()) {
            jdbc.execute("TRUNCATE TABLE ${tables.joinToString()} RESTART IDENTITY CASCADE")
        }
    }

    companion object {
        private const val FLYWAY_HISTORY = "flyway_schema_history"
        private const val JOBRUNR_MIGRATIONS = "jobrunr_migrations"

        val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:17")
                .withDatabaseName("open_banking_rails_test")
                .withUsername("postgres")
                .withPassword("postgres")
                .also { it.start() }
        }

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
