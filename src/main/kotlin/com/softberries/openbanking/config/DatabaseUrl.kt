package com.softberries.openbanking.config

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** JDBC settings derived from a Rails-style `DATABASE_URL` (`postgres://user:pass@host:5432/db?sslmode=require`). */
data class JdbcSettings(
    val url: String,
    val username: String?,
    val password: String?,
)

object DatabaseUrl {
    private const val DEFAULT_PORT = 5432
    private val schemes = setOf("postgres", "postgresql")

    fun parse(databaseUrl: String): JdbcSettings {
        val raw = databaseUrl.trim()
        if (raw.startsWith("jdbc:")) return JdbcSettings(raw, null, null)

        val uri = URI(raw)
        require(uri.scheme in schemes) { "DATABASE_URL must start with postgres:// or postgresql:// (got '${uri.scheme}')" }
        val host = requireNotNull(uri.host) { "DATABASE_URL has no host" }
        val port = if (uri.port == -1) DEFAULT_PORT else uri.port
        val database = uri.rawPath.orEmpty().removePrefix("/")
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()

        val userInfo = uri.rawUserInfo?.split(":", limit = 2)
        val username = userInfo?.getOrNull(0)?.takeIf { it.isNotEmpty() }?.let(::decode)
        val password = userInfo?.getOrNull(1)?.let(::decode)

        return JdbcSettings("jdbc:postgresql://$host:$port/$database$query", username, password)
    }

    private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)
}
