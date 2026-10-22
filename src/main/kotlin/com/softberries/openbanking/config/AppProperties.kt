package com.softberries.openbanking.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Files
import java.nio.file.Path
import java.time.DateTimeException
import java.time.ZoneId

/**
 * Instance-wide settings bound from the Ruby ENV names (relaxed binding: `APP_HOST` → `app.host`).
 * `FORCE_SSL` and `SECRET_KEY_BASE` keep their Ruby names and are mapped onto `app.*` in application.yml.
 * Enforcement of host/SSL settings belongs to the transport unit (M-002); this class only binds them.
 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    /** `APP_HOST`: public host[:port] without scheme; null when unset (config/environments/production.rb:61). */
    val host: String? = null,
    /** `APP_HOSTS`: raw comma-separated allowed hosts; null when unset, "" when set empty (compose default). */
    val hosts: String? = null,
    /** `APP_PROTOCOL`: scheme for absolute links, Ruby default `https` (production.rb:66). */
    val protocol: String = "https",
    /** `FORCE_SSL`: Ruby treats only the exact string "true" as on, default "true" (production.rb:30-31). */
    val forceSsl: String = "true",
    /** `SECRET_KEY_BASE`: master secret; required in production (see [ProductionSecretsCheck]). */
    val secretKeyBase: String = "",
    /** `APP_TIME_ZONE` (D-007): IANA zone for "today", periods and schedules. */
    val timeZone: String = DEFAULT_TIME_ZONE,
    /** File holding the release tag; the Docker build writes `APP_VERSION` into it. */
    val versionFile: String = "VERSION",
) {
    val zone: ZoneId =
        try {
            ZoneId.of(timeZone)
        } catch (e: DateTimeException) {
            throw IllegalArgumentException("APP_TIME_ZONE '$timeZone' is not a valid time zone id: ${e.message}", e)
        }

    /** `OpenBankingRails::ADMIN_EDITION` (config/application.rb:22). */
    val edition: String get() = EDITION

    /** `OpenBankingRails::ADMIN_VERSION` (config/application.rb:25-30): VERSION contents without a leading "v", else "dev". */
    val version: String by lazy { readVersion(Path.of(versionFile)) }

    val forceSslEnabled: Boolean get() = forceSsl == "true"

    /** Host used in absolute links (mailer default_url_options), Ruby default `localhost:3000`. */
    val publicHost: String get() = host ?: DEFAULT_PUBLIC_HOST

    /**
     * Allowed Host headers. Mirrors `ENV.fetch("APP_HOSTS", ENV.fetch("APP_HOST", ""))` (production.rb:99): APP_HOST is only
     * used when APP_HOSTS is unset, and an empty list disables the check.
     */
    val allowedHosts: List<String>
        get() = (hosts ?: host ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val EDITION = "Personal"
        const val DEFAULT_TIME_ZONE = "Europe/Warsaw"
        const val DEFAULT_PUBLIC_HOST = "localhost:3000"
        const val DEV_VERSION = "dev"

        fun readVersion(file: Path): String =
            if (Files.isRegularFile(file)) {
                Files.readString(file).trim().removePrefix("v")
            } else {
                DEV_VERSION
            }
    }
}
