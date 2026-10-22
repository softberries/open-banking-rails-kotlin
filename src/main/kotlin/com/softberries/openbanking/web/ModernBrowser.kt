package com.softberries.openbanking.web

/**
 * Port of Rails 8 `allow_browser versions: :modern` (`app/controllers/application_controller.rb:2`, C1, U-523): actionpack 8.1
 * `AllowBrowser::BrowserBlocker` on top of the `useragent` gem 0.16.11 (`Gemfile.lock:748`).
 *
 * Minimums: Chrome 120, Safari 17.2, Firefox 121, Opera 106; Internet Explorer is always rejected. Other browsers (as the gem
 * names them, e.g. Edge, Vivaldi, Android, PaleMoon), bots, requests without a User-Agent and user agents without a version
 * pass. The gem's classification is reproduced: product tokens, extension order and `bot?`.
 */
object ModernBrowser {
    private val minimumVersions =
        mapOf(
            "chrome" to Version.parse("120"),
            "safari" to Version.parse("17.2"),
            "firefox" to Version.parse("121"),
            "opera" to Version.parse("106"),
        )
    private const val IE = "internet explorer"

    /** True when [userAgent] names a guarded browser older than the `:modern` minimum (→ 406). */
    fun isBlocked(userAgent: String?): Boolean {
        if (userAgent.isNullOrBlank()) return false
        val browser = Parsed(UserAgent.parse(userAgent)).classify()
        if (browser.version.isNil) return false
        val name = browser.name?.lowercase() ?: return false
        // `ie: false` in the preset: guarded, and below any minimum.
        val below = name == IE || browser.version < (minimumVersions[name] ?: return false)
        return below && !browser.isBot
    }

    /** One `product/version (comment)` token of a user agent string (`UserAgent`). */
    data class UserAgent(
        val product: String,
        val version: Version,
        val comment: List<String>?,
    ) {
        companion object {
            /** `UserAgent::MATCHER`. */
            private val matcher = Regex("""^['"]*([^/\s]+)/?([^\s,]*)(\s\(([^)]*)\)|,gzip\(gfe\))?""")

            fun parse(value: String): List<UserAgent> {
                val agents = mutableListOf<UserAgent>()
                var rest = value
                while (true) {
                    val match = matcher.find(rest) ?: break
                    val comment = match.groups[4]?.value?.let { c -> if (c.isEmpty()) emptyList() else c.split("; ") }
                    agents += UserAgent(match.groupValues[1], Version.parse(match.groupValues[2]), comment)
                    rest = rest.substring(match.value.length).trim()
                }
                return agents
            }
        }
    }

    /** The browser the gem settles on: `browser`, `version` and `bot?` of the selected `UserAgent::Browsers` extension. */
    data class Browser(
        val name: String?,
        val version: Version,
        val isBot: Boolean,
    )

    /** `UserAgent::Browsers::ALL` in order; only the extensions that decide whether a guarded browser is reported. */
    private class Parsed(
        val agents: List<UserAgent>,
    ) {
        private val first = agents.firstOrNull()
        private val last = agents.lastOrNull()

        /** `application` of the Chrome, Vivaldi and WebKit extensions: the first token with a comment. */
        private val commented = agents.firstOrNull { !it.comment.isNullOrEmpty() }

        fun classify(): Browser =
            when {
                last?.product == "Edge" -> browser("Edge", last.version)
                isInternetExplorer() -> browser(IE, Version.parse(IE_VERSION.find(first!!.comment!!.joinToString("; "))?.groupValues?.get(2)))
                isOpera() -> browser("Opera", operaVersion())
                agents.any { it.product.contains("MicroMessenger", ignoreCase = true) } -> browser("Wechat Browser", product("MicroMessenger")?.version ?: Version.NIL)
                agents.any { it.product == "Vivaldi" } -> browser("Vivaldi", last!!.version, commented)
                agents.any { it.product == "Chrome" || it.product == "CriOS" } -> chrome()
                agents.any { it.product == "iTunes" } -> browser("iTunes", first!!.version)
                isPlayStation() -> browser("PlayStation", first!!.version)
                isWebkit() -> webkit()
                first?.product == "Mozilla" -> gecko()
                else -> browser(first?.product, first?.version ?: Version.NIL)
            }

        private fun browser(
            name: String?,
            version: Version,
            application: UserAgent? = first,
        ) = Browser(name, version, isBot(application))

        /** `Base#bot?`. */
        private fun isBot(application: UserAgent?): Boolean =
            application == null ||
                agents.any { a -> a.comment.orEmpty().any { BOT.containsMatchIn(it) } } ||
                product("Chrome-Lighthouse") != null ||
                application.product.contains("bot")

        private fun product(name: String) = agents.firstOrNull { it.product.equals(name, ignoreCase = true) }

        private fun isInternetExplorer(): Boolean {
            val comment = first?.comment ?: return false
            return comment.getOrNull(1)?.contains("MSIE") == true || TRIDENT.containsMatchIn(comment.joinToString("; "))
        }

        private fun isOpera() = first?.product == "Opera" || last?.product == "OPR"

        private fun operaVersion(): Version {
            if (first!!.comment.orEmpty().any { it.contains("Opera Mini") }) {
                return Version.parse(first.comment.orEmpty().firstNotNullOfOrNull { OPERA_MINI.find(it)?.groupValues?.get(1) })
            }
            return (product("Version") ?: product("OPR"))?.version ?: first.version
        }

        private fun chrome(): Browser {
            if (product("Iron") != null) return browser("Iron", Version.NIL, commented)
            val version = (product("CriOS") ?: product("Chrome"))!!.version
            return browser("Chrome", version, commented)
        }

        private fun isPlayStation(): Boolean {
            val comment = first?.comment?.firstOrNull() ?: return false
            return PLAYSTATIONS.any { comment.contains(it) }
        }

        private fun isWebkit() =
            agents.any { a ->
                a.product.equals("AppleWebKit", ignoreCase = true) || a.comment.orEmpty().any { WEBKIT_COMMENT.containsMatchIn(it) }
            }

        private fun webkit(): Browser {
            val comment = commented?.comment.orEmpty()
            val os = webkitOs(comment)
            val name =
                when {
                    os?.contains("Android") == true -> "Android"
                    comment.firstOrNull() == "BB10" -> "BlackBerry"
                    else -> "Safari"
                }
            val version =
                product("Version")?.version
                    ?: os?.let { IOS_NORMALIZED.find(it) }?.takeIf { name == "Safari" }?.let { Version.parse(it.groupValues[1]) }
                    ?: Version.parse(LEGACY_WEBKIT_BUILDS[webkitBuild()])
            return browser(name, version, commented)
        }

        /** `Webkit#os`, normalised far enough to tell Android and the iOS version. */
        private fun webkitOs(comment: List<String>): String? {
            if (comment.isEmpty()) return null
            val raw =
                when {
                    comment[0].contains("Windows NT") -> comment[0]
                    comment.getOrNull(2) == null -> comment.getOrNull(1)
                    comment[1].contains("Android") -> comment[1]
                    else -> comment.firstOrNull { IOS.containsMatchIn(it) } ?: comment[2]
                }
            return raw?.let { os -> IOS.find(os)?.let { "iOS ${it.groupValues[1].replace('_', '.')}" } ?: os }
        }

        private fun webkitBuild(): String? =
            product("AppleWebKit")?.version?.raw
                ?: agents.firstNotNullOfOrNull { a -> a.comment.orEmpty().firstNotNullOfOrNull { WEBKIT_COMMENT.find(it)?.groupValues?.get(1) } }

        private fun gecko(): Browser {
            val name = GECKO_BROWSERS.firstOrNull { product(it) != null } ?: return browser("Mozilla", first!!.version)
            val version = product(name)!!.version.takeUnless { it.isNil } ?: first!!.version
            return browser(name, version)
        }

        companion object {
            val IE_VERSION = Regex("""(MSIE\s|rv:)([\d.]+)""")
            val TRIDENT = Regex("""Trident.+rv:""")
            val BOT = Regex("bot", RegexOption.IGNORE_CASE)
            val OPERA_MINI = Regex("""Opera Mini/([\d.]+)""")
            val WEBKIT_COMMENT = Regex("""^AppleWebKit/([\d.]+)""", RegexOption.IGNORE_CASE)
            val IOS = Regex("""CPU (?:iPhone |iPod )?OS ([\d_]+) like Mac OS X""")
            val IOS_NORMALIZED = Regex("""iOS ([\d.]+)""")
            val PLAYSTATIONS = listOf("PLAYSTATION 3", "PlayStation Vita", "PlayStation 4")
            val GECKO_BROWSERS = listOf("PaleMoon", "Firefox", "Camino", "Iceweasel", "Seamonkey")

            /** `Webkit::BuildVersions`: Safari 1.0–5.1, all below 17.2. */
            val LEGACY_WEBKIT_BUILDS =
                mapOf(
                    "85.7" to "1.0", "85.8.5" to "1.0.3", "85.8.2" to "1.0.3", "124" to "1.2", "125.2" to "1.2.2",
                    "125.4" to "1.2.3", "125.5.5" to "1.2.4", "125.5.6" to "1.2.4", "125.5.7" to "1.2.4", "312.1.1" to "1.3",
                    "312.1" to "1.3", "312.5" to "1.3.1", "312.5.1" to "1.3.1", "312.5.2" to "1.3.1", "312.8" to "1.3.2",
                    "312.8.1" to "1.3.2", "412" to "2.0", "412.6" to "2.0", "412.6.2" to "2.0", "412.7" to "2.0.1",
                    "416.11" to "2.0.2", "416.12" to "2.0.2", "417.9" to "2.0.3", "418" to "2.0.3", "418.8" to "2.0.4",
                    "418.9" to "2.0.4", "418.9.1" to "2.0.4", "419" to "2.0.4", "425.13" to "2.2", "534.52.7" to "5.1.2",
                )
        }
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
