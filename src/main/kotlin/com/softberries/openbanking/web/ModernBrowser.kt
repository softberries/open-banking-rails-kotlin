package com.softberries.openbanking.web

/**
 * Port of Rails 8 `allow_browser versions: :modern` (`app/controllers/application_controller.rb:2`, C1, U-523): actionpack 8.1
 * `AllowBrowser::BrowserBlocker` on top of the `useragent` gem 0.16.11 (`Gemfile.lock:748`, classification in
 * [UserAgentClassifier]).
 *
 * Minimums: Chrome 120, Safari 17.2, Firefox 121, Opera 106; Internet Explorer is always rejected. Other browsers (as the gem
 * names them, e.g. Edge, Vivaldi, Android, PaleMoon), bots, requests without a User-Agent and user agents without a version
 * pass.
 */
object ModernBrowser {
    const val INTERNET_EXPLORER = "internet explorer"

    private val minimumVersions =
        mapOf(
            "chrome" to Version.parse("120"),
            "safari" to Version.parse("17.2"),
            "firefox" to Version.parse("121"),
            "opera" to Version.parse("106"),
        )

    /** True when [userAgent] names a guarded browser older than the `:modern` minimum (→ 406). */
    fun isBlocked(userAgent: String?): Boolean {
        if (userAgent.isNullOrBlank()) return false
        val browser = UserAgentClassifier.classify(userAgent)
        val name = browser.name?.lowercase()
        // `ie: false` in the preset: guarded, and below any minimum.
        val below = name == INTERNET_EXPLORER || minimumVersions[name]?.let { browser.version < it } == true
        return !browser.version.isNil && below && !browser.isBot
    }

    /**
     * `UserAgent::Version`: numeric segments (plus a trailing word) compared over six positions, missing ones count as 0. A
     * version that does not start with a digit is not comparable and counts as lower than anything but itself.
     */
    class Version private constructor(
        val raw: String,
    ) : Comparable<Version> {
        val isNil: Boolean = raw.isBlank()
        private val comparable = NUMERIC.matches(raw) || NUMERIC_PREFIX.containsMatchIn(raw)
        private val segments: List<Any> =
            if (comparable) SEGMENT.findAll(raw).map { it.value.toIntOrNull() ?: it.value }.toList() else emptyList()

        override fun compareTo(other: Version): Int {
            if (!comparable) return if (raw == other.raw) 0 else -1
            for (i in 0 until COMPARED_SEGMENTS) {
                val a = segments.getOrElse(i) { 0 }
                val b = other.segments.getOrElse(i) { 0 }
                val diff =
                    when {
                        a is String && b is Int -> -1
                        a is Int && b is String -> 1
                        a is Int && b is Int -> a.compareTo(b)
                        else -> (a as String).compareTo(b as String).coerceIn(-1, 1)
                    }
                if (diff != 0) return diff
            }
            return 0
        }

        override fun equals(other: Any?): Boolean = other is Version && other.raw == raw

        override fun hashCode(): Int = raw.hashCode()

        override fun toString(): String = raw

        companion object {
            private const val COMPARED_SEGMENTS = 6
            private val NUMERIC = Regex("""^\d+$""")
            private val NUMERIC_PREFIX = Regex("""^\d+\.""")
            private val SEGMENT = Regex("""\d+|[A-Za-z][0-9A-Za-z-]*$""")
            val NIL = Version("")

            fun parse(value: String?): Version = Version(value.orEmpty())
        }
    }
}
