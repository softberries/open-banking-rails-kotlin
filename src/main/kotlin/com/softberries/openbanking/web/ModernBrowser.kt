package com.softberries.openbanking.web

/**
 * Port of Rails 8 `allow_browser versions: :modern` (`app/controllers/application_controller.rb:2`, C1, U-523) with the
 * `useragent` gem's classification: Chrome 120, Safari 17.2, Firefox 121, Opera 106; Internet Explorer is always rejected.
 * Other browsers, bots, requests without a User-Agent and user agents without a version are let through.
 */
object ModernBrowser {
    private val minimumVersions =
        mapOf(
            Browser.CHROME to Version.parse("120"),
            Browser.SAFARI to Version.parse("17.2"),
            Browser.FIREFOX to Version.parse("121"),
            Browser.OPERA to Version.parse("106"),
        )

    private val bot = Regex("bot|crawl|spider|slurp", RegexOption.IGNORE_CASE)
    private val ie = Regex("""MSIE ([\d.]+)|Trident/[\d.]+.*rv:([\d.]+)""")
    private val edge = Regex("""\b(?:Edge?|EdgA|EdgiOS)/[\d.]+""")
    private val opera = Regex("""\bOPR/([\d.]+)""")
    private val operaPresto = Regex("""^Opera/.*Version/([\d.]+)""")
    private val firefox = Regex("""\bFirefox/([\d.]+)""")
    private val chrome = Regex("""\b(?:Chrome|CriOS)/([\d.]+)""")
    private val safari = Regex("""\bVersion/([\d.]+).*\bSafari/""")

    enum class Browser { CHROME, SAFARI, FIREFOX, OPERA, IE }

    /** True when [userAgent] names a guarded browser older than the `:modern` minimum (→ 406). */
    fun isBlocked(userAgent: String?): Boolean {
        if (userAgent.isNullOrBlank() || bot.containsMatchIn(userAgent)) return false
        val (browser, version) = classify(userAgent) ?: return false
        if (browser == Browser.IE) return true
        return version < minimumVersions.getValue(browser)
    }

    private fun classify(ua: String): Pair<Browser, Version>? {
        ie.find(ua)?.let { m -> return Browser.IE to Version.parse(m.groupValues.drop(1).first { it.isNotEmpty() }) }
        if (edge.containsMatchIn(ua)) return null
        val guarded =
            listOf(
                Browser.OPERA to opera,
                Browser.OPERA to operaPresto,
                Browser.FIREFOX to firefox,
                Browser.CHROME to chrome,
                Browser.SAFARI to safari,
            )
        for ((browser, pattern) in guarded) {
            pattern.find(ua)?.let { return browser to Version.parse(it.groupValues[1]) }
        }
        return null
    }

    /** `UserAgent::Version` comparison: numeric segments, missing segments count as 0. */
    data class Version(
        val segments: List<Int>,
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            for (i in 0 until maxOf(segments.size, other.segments.size)) {
                val diff = segments.getOrElse(i) { 0 }.compareTo(other.segments.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }

        companion object {
            fun parse(value: String): Version = Version(value.split('.').mapNotNull { it.toIntOrNull() })
        }
    }
}
