package com.softberries.openbanking.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders

/**
 * The request URL as Rails sees it behind a reverse proxy (`ActionDispatch::Http::URL`, Rack `Request#scheme`):
 * - host and port: the last `X-Forwarded-Host` entry, else `Host`; the port only from that value (`url.rb:312-357`);
 * - scheme: HTTPS (real TLS or assume_ssl), `X-Forwarded-Ssl: on`, `Forwarded: proto=`, the last allowed
 *   `X-Forwarded-Proto` / `X-Forwarded-Scheme` value, else the connector's scheme (rack 3.2 `request.rb:254-264,746-768`).
 */
object RailsRequestUrl {
    const val X_FORWARDED_HOST = "X-Forwarded-Host"
    private const val FORWARDED = "Forwarded"
    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
    private val ALLOWED_SCHEMES = setOf("https", "http", "wss", "ws")
    private val SECURE_SCHEMES = setOf("https", "wss")
    private val PORT = Regex(""":(\d+)$""")
    private val HEADER_LIST = Regex("""[,\s]+""")
    private val FORWARDED_HOSTS = Regex(""",\s?""")

    /** `request.base_url`, e.g. `https://finance.example.com` or `http://localhost:3000`. */
    fun baseUrl(request: HttpServletRequest): String {
        val https = scheme(request) in SECURE_SCHEMES
        val raw = rawHostWithPort(request)
        val port =
            PORT
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        val standard = port == null || port == (if (https) HTTPS_PORT else HTTP_PORT)
        return "${if (https) "https" else "http"}://${raw.replace(PORT, "")}${if (standard) "" else ":$port"}"
    }

    /** `raw_host_with_port`. */
    fun rawHostWithPort(request: HttpServletRequest): String =
        request
            .getHeader(X_FORWARDED_HOST)
            ?.takeIf { it.isNotBlank() }
            ?.split(FORWARDED_HOSTS)
            ?.last()
            ?: request.getHeader(HttpHeaders.HOST)
            ?: "${request.serverName}:${request.serverPort}"

    /** Rack `Request#scheme`. */
    fun scheme(request: HttpServletRequest): String =
        when {
            request.isSecure -> "https"
            request.getHeader("X-Forwarded-Ssl") == "on" -> "https"
            else -> forwardedScheme(request) ?: request.scheme
        }

    private fun forwardedScheme(request: HttpServletRequest): String? {
        val forwarded = forwardedProtos(request.getHeader(FORWARDED)).lastOrNull()
        if (forwarded != null && forwarded in ALLOWED_SCHEMES) return forwarded
        return listOf("X-Forwarded-Proto", "X-Forwarded-Scheme").firstNotNullOfOrNull { header ->
            splitHeader(request.getHeader(header)).asReversed().firstOrNull { it in ALLOWED_SCHEMES }
        }
    }

    /** `proto=` values of an RFC 7239 `Forwarded` header, in order. */
    private fun forwardedProtos(header: String?): List<String> =
        header
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.startsWith("proto=", ignoreCase = true) }
            ?.map { it.substringAfter('=').trim('"') }
            .orEmpty()

    private fun splitHeader(value: String?): List<String> =
        value
            ?.trim()
            ?.split(HEADER_LIST)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
}
