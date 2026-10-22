package com.softberries.openbanking.config

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single source of "now" and "today". Timestamps are UTC instants; calendar dates are evaluated in the
 * instance zone `APP_TIME_ZONE` (D-007, deviation X-18 — Ruby evaluates them in UTC).
 */
class AppClock(
    val zone: ZoneId,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun now(): Instant = clock.instant()

    fun today(): LocalDate = LocalDate.ofInstant(now(), zone)
}
