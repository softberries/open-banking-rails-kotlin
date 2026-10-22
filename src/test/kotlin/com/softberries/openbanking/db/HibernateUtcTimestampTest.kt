package com.softberries.openbanking.db

import com.softberries.openbanking.support.IntegrationTest
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDateTime
import java.util.Date
import java.util.TimeZone

/** Test-only mapping of the PaperTrail `versions` table, used to observe how timestamps are written. */
@Entity
@Table(name = "versions")
class VersionProbe(
    @Column(name = "item_type") val itemType: String,
    @Column(name = "item_id") val itemId: Long,
    @Column(name = "event") val event: String,
    @Column(name = "created_at") val createdAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

/** Same table, mapped with a zone-sensitive JDBC type: without `hibernate.jdbc.time_zone=UTC` it is written in the JVM zone. */
@Entity
@Table(name = "versions")
class LegacyDateVersionProbe(
    @Column(name = "item_type") val itemType: String,
    @Column(name = "item_id") val itemId: Long,
    @Column(name = "event") val event: String,
    @Column(name = "created_at") @Temporal(TemporalType.TIMESTAMP) val createdAt: Date,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

class HibernateUtcTimestampTest : IntegrationTest() {
    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactions: TransactionTemplate

    @Autowired
    private lateinit var dataSource: HikariDataSource

    private val originalZone: TimeZone = TimeZone.getDefault()

    @AfterEach
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
        dataSource.hikariPoolMXBean.softEvictConnections()
    }

    @Test
    fun `a timestamp written under JVM zone Europe Warsaw is stored as its UTC wall time`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"))
        // pgjdbc sets the session TimeZone from the JVM zone when a connection opens; force fresh connections.
        dataSource.hikariPoolMXBean.softEvictConnections()
        val instant = Instant.parse("2026-10-21T22:30:00Z")

        transactions.executeWithoutResult { entityManager.persist(VersionProbe("Probe", 1, "create", instant)) }

        val stored = jdbc.queryForObject("SELECT created_at::text FROM versions", String::class.java)
        LocalDateTime.parse(stored!!.replace(' ', 'T')) shouldBe LocalDateTime.parse("2026-10-21T22:30:00")
        transactions
            .execute { entityManager.createQuery("select v from VersionProbe v", VersionProbe::class.java).singleResult }!!
            .createdAt shouldBe instant
    }

    @Test
    fun `zone-sensitive JDBC timestamps are also written in UTC under JVM zone Europe Warsaw`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"))
        dataSource.hikariPoolMXBean.softEvictConnections()
        val instant = Instant.parse("2026-10-21T22:30:00Z")

        transactions.executeWithoutResult { entityManager.persist(LegacyDateVersionProbe("Probe", 1, "create", Date.from(instant))) }

        val stored = jdbc.queryForObject("SELECT created_at::text FROM versions", String::class.java)
        LocalDateTime.parse(stored!!.replace(' ', 'T')) shouldBe LocalDateTime.parse("2026-10-21T22:30:00")
    }
}
