package com.softberries.openbanking.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `ActionDispatch::HostAuthorization` with `config.hosts = APP_HOSTS` (production.rb:95-103): a `Host` (and the last
 * `X-Forwarded-Host`) outside the list gets an empty 403. An entry matches the host with any port unless it names a port;
 * a leading dot also allows subdomains. `/up` is exempt. An empty list disables the check.
 */
class HostAuthorizationFilter(
    allowedHosts: List<String>,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val patterns = allowedHosts.map(::pattern)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = patterns.isEmpty() || WebPaths.isUp(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val host = request.getHeader(HttpHeaders.HOST) ?: request.serverName
        val forwarded =
            request
                .getHeader(X_FORWARDED_HOST)
                ?.split(Regex(""",\s?"""))
                ?.lastOrNull()
                ?.takeIf { it.isNotBlank() }
        if (allows(host) && (forwarded == null || allows(forwarded))) {
            chain.doFilter(request, response)
            return
        }
        log.error(
            "[ActionDispatch::HostAuthorization::DefaultResponseApp] Blocked hosts: {}",
            listOfNotNull(host, forwarded).joinToString(),
        )
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = HtmlErrorPages.HTML_UTF8
        response.setContentLength(0)
    }

    private fun allows(host: String): Boolean = patterns.any { it.matches(host) }

    companion object {
        const val X_FORWARDED_HOST = "X-Forwarded-Host"
        private const val PORT = """(:\d+)?"""

        private fun pattern(entry: String): Regex =
            if (entry.startsWith(".")) {
                Regex("""(.+\.)?${Regex.escape(entry.substring(1))}$PORT""", RegexOption.IGNORE_CASE)
            } else {
                Regex("""${Regex.escape(entry)}$PORT""", RegexOption.IGNORE_CASE)
            }
    }
}
