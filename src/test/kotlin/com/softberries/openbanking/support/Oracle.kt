package com.softberries.openbanking.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path

/** Reads runtime samples of the Ruby service from `migration-spec/oracle/http/` (the parity reference). */
object Oracle {
    private val root: Path = Path.of("migration-spec", "oracle", "http")
    private val mapper = ObjectMapper()

    fun case(
        file: String,
        case: String,
    ): JsonNode {
        val cases = mapper.readTree(root.resolve(file).toFile()).path("cases")
        return cases.firstOrNull { it.path("case").asText() == case }
            ?: error("oracle case '$case' not found in $file")
    }
}
