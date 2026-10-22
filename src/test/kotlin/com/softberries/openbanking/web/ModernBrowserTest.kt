package com.softberries.openbanking.web

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * UA matrix for the browser gate: each `:modern` threshold ±1 version, IE, browsers the `useragent` gem does not guard (Edge,
 * Vivaldi, Android, PaleMoon, …), iOS browsers it reports as Safari, bots and user agents without a version. The expected values
 * in `web/modern-browser-matrix.tsv` were produced by the Ruby gem itself.
 */
class ModernBrowserTest {
    @ParameterizedTest(name = "{1} -> blocked={0}: {2}")
    @MethodSource("matrix")
    fun `classifies user agents like Rails allow_browser modern`(
        blocked: Boolean,
        @Suppress("UnusedParameter") browser: String,
        userAgent: String,
    ) {
        ModernBrowser.isBlocked(userAgent) shouldBe blocked
    }

    @Test
    fun `a request without User-Agent passes`() {
        ModernBrowser.isBlocked(null) shouldBe false
        ModernBrowser.isBlocked("") shouldBe false
        ModernBrowser.isBlocked("   ") shouldBe false
    }

    @Test
    fun `versions compare like UserAgent Version`() {
        (ModernBrowser.Version.parse("17.1.2") < ModernBrowser.Version.parse("17.2")) shouldBe true
        (ModernBrowser.Version.parse("17.2.0") < ModernBrowser.Version.parse("17.2")) shouldBe false
        (ModernBrowser.Version.parse("120.0.6099") < ModernBrowser.Version.parse("120")) shouldBe false
        (ModernBrowser.Version.parse("beta") < ModernBrowser.Version.parse("120")) shouldBe true
        (ModernBrowser.Version.parse("120.0b") < ModernBrowser.Version.parse("120.0.1")) shouldBe true
    }

    companion object {
        @JvmStatic
        fun matrix(): List<Arguments> =
            ModernBrowserTest::class.java
                .getResource("/web/modern-browser-matrix.tsv")!!
                .readText()
                .lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val (blocked, browser, userAgent) = line.split('\t', limit = 3)
                    Arguments.of(blocked.toBooleanStrict(), browser, userAgent)
                }
    }
}
