package com.softberries.openbanking.web

import com.softberries.openbanking.web.ModernBrowser.Version

/** One `product/version (comment)` token of a User-Agent (`UserAgent` of the `useragent` gem). */
data class UserAgentToken(
    val product: String,
    val version: Version,
    val comment: List<String>?,
)

/** What the gem reports for a User-Agent: `browser`, `version` and `bot?` of the selected browser extension. */
data class DetectedBrowser(
    val name: String?,
    val version: Version,
    val isBot: Boolean,
)

/** The parsed tokens (`UserAgent::Browsers::Base`) with the lookups the browser extensions share. */
class UserAgentTokens(
    val agents: List<UserAgentToken>,
) {
    val first = agents.firstOrNull()
    val last = agents.lastOrNull()

    /** `application` of the Chrome, Vivaldi and WebKit extensions: the first token with a comment. */
    val commented = agents.firstOrNull { !it.comment.isNullOrEmpty() }

    /** `detect_product`: case-insensitive. */
    fun product(name: String): UserAgentToken? = agents.firstOrNull { it.product.equals(name, ignoreCase = true) }

    fun hasProduct(vararg names: String): Boolean = agents.any { it.product in names }

    fun browser(
        name: String?,
        version: Version,
        application: UserAgentToken? = first,
    ) = DetectedBrowser(name, version, isBot(application))

    /** `Base#bot?`. */
    private fun isBot(application: UserAgentToken?): Boolean =
        application == null ||
            agents.any { a -> a.comment.orEmpty().any { BOT.containsMatchIn(it) } } ||
            product("Chrome-Lighthouse") != null ||
            application.product.contains("bot")

    companion object {
        private val BOT = Regex("bot", RegexOption.IGNORE_CASE)

        /** `UserAgent::MATCHER`. */
        private val MATCHER = Regex("""^['"]*([^/\s]+)/?([^\s,]*)(\s\(([^)]*)\)|,gzip\(gfe\))?""")
        private const val COMMENT_GROUP = 4

        /** `UserAgent.parse`. */
        fun parse(value: String): UserAgentTokens {
            val agents = mutableListOf<UserAgentToken>()
            var rest = value
            while (true) {
                val match = MATCHER.find(rest) ?: break
                val comment = match.groups[COMMENT_GROUP]?.value?.let { c -> if (c.isEmpty()) emptyList() else c.split("; ") }
                agents += UserAgentToken(match.groupValues[1], Version.parse(match.groupValues[2]), comment)
                rest = rest.substring(match.value.length).trim()
            }
            return UserAgentTokens(agents)
        }
    }
}

/**
 * `UserAgent::Browsers.extend` of `useragent` 0.16.11: the first matching extension of `UserAgent::Browsers::ALL`, in the gem's
 * order. Extensions that never name a guarded browser (media players, podcast apps) fall through to the base case, which names
 * the first product, as the gem does.
 */
object UserAgentClassifier {
    private class Extension(
        val matches: (UserAgentTokens) -> Boolean,
        val detect: (UserAgentTokens) -> DetectedBrowser,
    )

    private val extensions =
        listOf(
            Extension({ it.last?.product == "Edge" }) { it.browser("Edge", it.last!!.version) },
            Extension(::isInternetExplorer) { it.browser(ModernBrowser.INTERNET_EXPLORER, ieVersion(it)) },
            Extension({ it.first?.product == "Opera" || it.last?.product == "OPR" }, ::opera),
            Extension({ t -> t.agents.any { it.product.contains("MicroMessenger", ignoreCase = true) } }) {
                it.browser("Wechat Browser", it.product("MicroMessenger")?.version ?: Version.NIL)
            },
            Extension({ it.hasProduct("Vivaldi") }) { it.browser("Vivaldi", it.last!!.version, it.commented) },
            Extension({ it.hasProduct("Chrome", "CriOS") }, ::chrome),
            Extension({ it.hasProduct("iTunes") }) { it.browser("iTunes", it.first!!.version) },
            Extension(::isPlayStation) { it.browser("PlayStation", it.first!!.version) },
            Extension({ it.product("AppleWebKit") != null || webkitCommentBuild(it) != null }, ::webkit),
            Extension({ it.first?.product == "Mozilla" }, ::gecko),
        )

    private val TRIDENT = Regex("""Trident.+rv:""")
    private val IE_VERSION = Regex("""(MSIE\s|rv:)([\d.]+)""")
    private val OPERA_MINI = Regex("""Opera Mini/([\d.]+)""")
    private val WEBKIT_COMMENT = Regex("""^AppleWebKit/([\d.]+)""", RegexOption.IGNORE_CASE)
    private val IOS = Regex("""CPU (?:iPhone |iPod )?OS ([\d_]+) like Mac OS X""")
    private val IOS_NORMALIZED = Regex("""iOS ([\d.]+)""")
    private val PLAYSTATIONS = listOf("PLAYSTATION 3", "PlayStation Vita", "PlayStation 4")
    private val GECKO_BROWSERS = listOf("PaleMoon", "Firefox", "Camino", "Iceweasel", "Seamonkey")

    /** `Webkit::BuildVersions`: Safari 1.0–5.1. */
    private val LEGACY_WEBKIT_BUILDS: Map<String, String> =
        (
            "85.7=1.0 85.8.5=1.0.3 85.8.2=1.0.3 124=1.2 125.2=1.2.2 125.4=1.2.3 125.5.5=1.2.4 125.5.6=1.2.4 125.5.7=1.2.4 " +
                "312.1.1=1.3 312.1=1.3 312.5=1.3.1 312.5.1=1.3.1 312.5.2=1.3.1 312.8=1.3.2 312.8.1=1.3.2 412=2.0 412.6=2.0 " +
                "412.6.2=2.0 412.7=2.0.1 416.11=2.0.2 416.12=2.0.2 417.9=2.0.3 418=2.0.3 418.8=2.0.4 418.9=2.0.4 418.9.1=2.0.4 " +
                "419=2.0.4 425.13=2.2 534.52.7=5.1.2"
        ).split(' ').associate { it.substringBefore('=') to it.substringAfter('=') }

    fun classify(userAgent: String): DetectedBrowser {
        val tokens = UserAgentTokens.parse(userAgent)
        val extension = extensions.firstOrNull { it.matches(tokens) }
        return extension?.detect(tokens) ?: tokens.browser(tokens.first?.product, tokens.first?.version ?: Version.NIL)
    }

    private fun isInternetExplorer(tokens: UserAgentTokens): Boolean {
        val comment = tokens.first?.comment ?: return false
        return comment.getOrNull(1)?.contains("MSIE") == true || TRIDENT.containsMatchIn(comment.joinToString("; "))
    }

    private fun ieVersion(tokens: UserAgentTokens): Version =
        Version.parse(
            IE_VERSION
                .find(
                    tokens.first!!
                        .comment
                        .orEmpty()
                        .joinToString("; "),
                )?.groupValues
                ?.get(2),
        )

    private fun opera(tokens: UserAgentTokens): DetectedBrowser {
        val first = tokens.first!!
        val version =
            if (first.comment.orEmpty().any { it.contains("Opera Mini") }) {
                Version.parse(first.comment.orEmpty().firstNotNullOfOrNull { OPERA_MINI.find(it)?.groupValues?.get(1) })
            } else {
                (tokens.product("Version") ?: tokens.product("OPR"))?.version ?: first.version
            }
        return tokens.browser("Opera", version)
    }

    private fun chrome(tokens: UserAgentTokens): DetectedBrowser {
        if (tokens.product("Iron") != null) return tokens.browser("Iron", Version.NIL, tokens.commented)
        val version = (tokens.product("CriOS") ?: tokens.product("Chrome"))!!.version
        return tokens.browser("Chrome", version, tokens.commented)
    }

    private fun isPlayStation(tokens: UserAgentTokens): Boolean {
        val comment = tokens.first?.comment?.firstOrNull() ?: return false
        return PLAYSTATIONS.any { comment.contains(it) }
    }

    private fun webkitCommentBuild(tokens: UserAgentTokens): String? =
        tokens.agents
            .asSequence()
            .flatMap { it.comment.orEmpty() }
            .firstNotNullOfOrNull { WEBKIT_COMMENT.find(it)?.groupValues?.get(1) }

    /** `Webkit#browser` / `#version`: Android and BlackBerry are not Safari; iOS browsers without `Version/` use the OS version. */
    private fun webkit(tokens: UserAgentTokens): DetectedBrowser {
        val comment = tokens.commented?.comment.orEmpty()
        val os = webkitOs(comment)
        val name =
            when {
                os?.contains("Android") == true -> "Android"
                comment.firstOrNull() == "BB10" -> "BlackBerry"
                else -> "Safari"
            }
        val iosVersion = os?.let { IOS_NORMALIZED.find(it)?.groupValues?.get(1) }?.takeIf { name == "Safari" }
        val build = tokens.product("AppleWebKit")?.version?.raw ?: webkitCommentBuild(tokens)
        val version =
            tokens.product("Version")?.version
                ?: iosVersion?.let(Version::parse)
                ?: Version.parse(LEGACY_WEBKIT_BUILDS[build])
        return tokens.browser(name, version, tokens.commented)
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

    private fun gecko(tokens: UserAgentTokens): DetectedBrowser {
        val first = tokens.first!!
        val name = GECKO_BROWSERS.firstOrNull { tokens.product(it) != null } ?: return tokens.browser("Mozilla", first.version)
        val version = tokens.product(name)!!.version.takeUnless { it.isNil } ?: first.version
        return tokens.browser(name, version)
    }
}
