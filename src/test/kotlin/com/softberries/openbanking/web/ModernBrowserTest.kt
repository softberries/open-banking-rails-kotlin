package com.softberries.openbanking.web

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** UA matrix for the browser gate: each `:modern` threshold ±1 version, IE, unguarded browsers, bots, no version. */
class ModernBrowserTest {
    @ParameterizedTest(name = "{0} -> blocked={2}")
    @MethodSource("cases")
    fun `classifies user agents like Rails allow_browser modern`(
        @Suppress("UnusedParameter") name: String,
        userAgent: String,
        blocked: Boolean,
    ) {
        ModernBrowser.isBlocked(userAgent) shouldBe blocked
    }

    @Test
    fun `a request without User-Agent passes`() {
        ModernBrowser.isBlocked(null) shouldBe false
        ModernBrowser.isBlocked("") shouldBe false
    }

    @Test
    fun `versions compare segment by segment`() {
        (ModernBrowser.Version.parse("17.1.2") < ModernBrowser.Version.parse("17.2")) shouldBe true
        (ModernBrowser.Version.parse("17.2.0") < ModernBrowser.Version.parse("17.2")) shouldBe false
        (ModernBrowser.Version.parse("120.0.6099") < ModernBrowser.Version.parse("120")) shouldBe false
    }

    companion object {
        private const val WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
        private const val MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)"
        private const val IOS = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)"

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                Arguments.of("Chrome 119", "$WIN Chrome/119.0.6045.199 Safari/537.36", true),
                Arguments.of("Chrome 120", "$WIN Chrome/120.0.0.0 Safari/537.36", false),
                Arguments.of(
                    "Chrome 121",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                    false,
                ),
                Arguments.of("Chrome iOS 119", "$IOS CriOS/119.0.6045.169 Mobile/15E148 Safari/604.1", true),
                Arguments.of("Safari 17.1", "$MAC Version/17.1 Safari/605.1.15", true),
                Arguments.of("Safari 17.1.2", "$MAC Version/17.1.2 Safari/605.1.15", true),
                Arguments.of("Safari 17.2", "$MAC Version/17.2 Safari/605.1.15", false),
                Arguments.of(
                    "Safari 17.3",
                    "$IOS Version/17.3 Mobile/15E148 Safari/604.1",
                    false,
                ),
                Arguments.of("Firefox 120", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0", true),
                Arguments.of("Firefox 121", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0", false),
                Arguments.of("Firefox 122", "Mozilla/5.0 (X11; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0", false),
                Arguments.of("Opera 105", "$WIN Chrome/119.0.0.0 Safari/537.36 OPR/105.0.0.0", true),
                Arguments.of("Opera 106", "$WIN Chrome/120.0.0.0 Safari/537.36 OPR/106.0.0.0", false),
                Arguments.of("Opera 107 on old Chrome", "$WIN Chrome/100.0.0.0 Safari/537.36 OPR/107.0.0.0", false),
                Arguments.of("Opera Presto", "Opera/9.80 (Windows NT 6.1) Presto/2.12.388 Version/12.16", true),
                Arguments.of("IE 11", "Mozilla/5.0 (Windows NT 10.0; Trident/7.0; rv:11.0) like Gecko", true),
                Arguments.of("IE 6", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)", true),
                Arguments.of("Edge is not guarded", "$WIN Chrome/100.0.0.0 Safari/537.36 Edg/100.0.0.0", false),
                Arguments.of("Googlebot", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", false),
                Arguments.of("curl", "curl/8.4.0", false),
                Arguments.of("no version", "SomeClient", false),
            )
    }
}
