package com.softberries.openbanking.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `FORCE_SSL=true` in production (C4, 70-security-and-config.md "TLS"). Rails turns on `assume_ssl` and `force_ssl` together:
 * `ActionDispatch::AssumeSSL` marks every request as HTTPS (TLS terminates at the reverse proxy), so absolute URLs use
 * `https://`; `ActionDispatch::SSL` then sends HSTS and flags cookies `Secure`. A request that is still plain HTTP (only
 * possible without assume_ssl) is redirected 301 to HTTPS; `/up` is never redirected.
 */
class TransportSecurityFilter(
    private val assumeSsl: Boolean = true,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val req = if (assumeSsl) AssumeSslRequest(request) else request
        if (!req.isSecure && !WebPaths.isUp(req)) {
            redirectToHttps(req, response)
            return
        }
        response.setHeader(HSTS_HEADER, HSTS_VALUE)
        chain.doFilter(req, SecureCookiesResponse(response))
    }

    private fun redirectToHttps(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val query = request.queryString?.let { "?$it" }.orEmpty()
        val status =
            if (request.method == "GET" || request.method == "HEAD") {
                HttpServletResponse.SC_MOVED_PERMANENTLY
            } else {
                PERMANENT_REDIRECT
            }
        response.status = status
        response.setHeader("Location", "https://${request.serverName}${request.requestURI}$query")
        response.contentType = HtmlErrorPages.HTML_UTF8
        response.setContentLength(0)
    }

    private class AssumeSslRequest(
        request: HttpServletRequest,
    ) : HttpServletRequestWrapper(request) {
        override fun isSecure(): Boolean = true

        override fun getScheme(): String = "https"

        override fun getServerPort(): Int = HTTPS_PORT

        override fun getRequestURL(): StringBuffer = StringBuffer("https://").append(serverName).append(requestURI)
    }

    private class SecureCookiesResponse(
        response: HttpServletResponse,
    ) : HttpServletResponseWrapper(response) {
        override fun addCookie(cookie: Cookie) {
            cookie.secure = true
            super.addCookie(cookie)
        }
    }

    companion object {
        const val HSTS_HEADER = "Strict-Transport-Security"

        /** `ActionDispatch::SSL` default: 2 years, include subdomains, no preload. */
        const val HSTS_VALUE = "max-age=63072000; includeSubDomains"
        private const val HTTPS_PORT = 443
        private const val PERMANENT_REDIRECT = 308
    }
}
