package com.softberries.openbanking.db

import com.softberries.openbanking.support.IntegrationTest
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Checks the migrated schema against the data specification `migration-spec/docs/50-data.md` (sections 2 and 5). */
class FlywayBaselineTest : IntegrationTest() {
    private data class Column(
        val name: String,
        val type: String,
        val nullable: Boolean,
    )

    private val spec: Map<String, List<Column>> by lazy { parseSpecTables() }

    @Test
    fun `flyway applied only the baseline`() {
        val history = jdbc.queryForList("SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL")
        history.map { it["version"] } shouldContainExactly listOf("1")
        history.single()["success"] shouldBe true
    }

    @Test
    fun `every table of 50-data section 2 exists with the documented columns, types and nullability`() {
        val actualTables =
            jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename NOT IN ('flyway_schema_history') " +
                    "AND tablename NOT LIKE 'jobrunr%'",
                String::class.java,
            )
        actualTables shouldContainExactlyInAnyOrder spec.keys

        assertSoftly {
            spec.forEach { (table, columns) ->
                val actual =
                    jdbc.query(
                        """
                        SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, NOT a.attnotnull AS nullable
                        FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'public' AND c.relname = ? AND a.attnum > 0 AND NOT a.attisdropped
                        """.trimIndent(),
                        { rs, _ -> Column(rs.getString(1), normalise(rs.getString(2)), rs.getBoolean(3)) },
                        table,
                    )
                withClue(table) { actual shouldContainExactlyInAnyOrder columns }
            }
        }
    }

    @Test
    fun `no gutentag tables and no Rails bookkeeping tables`() {
        jdbc
            .queryForList(
                "SELECT tablename FROM pg_tables " +
                    "WHERE tablename LIKE 'gutentag%' OR tablename IN ('schema_migrations', 'ar_internal_metadata')",
                String::class.java,
            ).shouldBeEmpty()
    }

    @Test
    fun `ltree extension is installed`() {
        jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'ltree'", Int::class.java) shouldBe 1
    }

    @Test
    fun `ledger_entries view v04 exposes the documented columns`() {
        val columns =
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'ledger_entries' ORDER BY ordinal_position",
                String::class.java,
            )
        columns shouldContainExactly
            listOf(
                "source_type",
                "source_id",
                "bank_account_id",
                "amount_cents",
                "currency",
                "direction",
                "signed_amount_cents",
                "status",
                "booking_date",
                "transaction_date",
                "payment_method",
                "title",
                "counterparty_name",
                "counterparty_kind",
                "enrichment_id",
                "merchant_id",
                "effective_category_id",
                "category_path",
                "essential",
                "enrichment_source",
            )
    }

    @Test
    fun `ownership xor check constraint exists on bank_accounts`() {
        val definition =
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'bank_accounts_ownership_xor' AND contype = 'c'",
                String::class.java,
            )
        definition shouldContain "manual = true"
        definition shouldContain "tpp_credential_id IS NULL"
        definition shouldContain "manual_owner_id IS NOT NULL"
    }

    @Test
    fun `26 foreign keys, all NO ACTION`() {
        val fks =
            jdbc.queryForList(
                "SELECT confdeltype::text AS del, confupdtype::text AS upd FROM pg_constraint " +
                    "WHERE contype = 'f' AND connamespace = 'public'::regnamespace",
            )
        fks.size shouldBe 26
        fks.map { it["del"] to it["upd"] }.toSet() shouldBe setOf("a" to "a")
    }

    @Test
    fun `partial unique, partial and GiST indexes exist`() {
        fun indexDef(name: String): String =
            jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = ?", String::class.java, name)!!

        assertSoftly {
            indexDef("index_one_primary_tpp_credential_per_user") shouldContain "UNIQUE INDEX"
            indexDef("index_one_primary_tpp_credential_per_user") shouldContain "WHERE (\"primary\" = true)"
            indexDef("idx_manual_transactions_one_per_linked_bank_tx") shouldContain "UNIQUE INDEX"
            indexDef("idx_manual_transactions_one_per_linked_bank_tx") shouldContain "WHERE (linked_bank_transaction_id IS NOT NULL)"
            indexDef("index_operation_runs_scheduled_for_idempotency") shouldContain "UNIQUE INDEX"
            indexDef("index_operation_runs_scheduled_for_idempotency") shouldContain "(subject_type, subject_id, kind, scheduled_for)"
            indexDef("index_operation_runs_scheduled_for_idempotency") shouldContain "WHERE (scheduled_for IS NOT NULL)"
            indexDef("index_sync_schedules_due") shouldContain "(enabled, next_run_at) WHERE (enabled = true)"
            indexDef("index_categories_on_path") shouldContain "USING gist (path)"
        }
    }

    private fun parseSpecTables(): Map<String, List<Column>> {
        val lines = Files.readAllLines(Path.of("migration-spec", "docs", "50-data.md"))
        val section = lines.dropWhile { !it.startsWith("## 2. Tables") }.drop(1).takeWhile { !it.startsWith("## 3.") }
        val tables = linkedMapOf<String, MutableList<Column>>()
        var current: String? = null
        for (line in section) {
            val heading = Regex("^### (\\w+)\\s*$").find(line)?.groupValues?.get(1)
            val row = Regex("^\\| `(\\w+)` \\| ([^|]+) \\| (NO|yes) \\|").find(line)
            when {
                heading != null -> {
                    current = heading.takeUnless { it.startsWith("gutentag_") }
                    current?.let { tables[it] = mutableListOf() }
                }

                line.startsWith("### ") -> {
                    current = null
                }

                row != null && current != null -> {
                    val (name, type, nullable) = row.destructured
                    tables.getValue(current) += Column(name, type.trim().removeSuffix(" (bigserial)"), nullable == "yes")
                }
            }
        }
        return tables
    }

    private fun normalise(pgType: String): String =
        pgType
            .replace("timestamp(6) without time zone", "timestamp(6)")
            .replace("character varying", "varchar")
            .removePrefix("public.")
}
