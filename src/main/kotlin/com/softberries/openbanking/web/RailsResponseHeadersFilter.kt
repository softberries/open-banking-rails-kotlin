package com.softberries.openbanking.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Rails' `action_dispatch.default_headers` (load_defaults 8.1) on every response, plus Rails' default `Cache-Control`:
 * `max-age=0, private, must-revalidate` for a 200/201 (ETag middleware), `no-cache` for everything else, unless
 * the handler set its own. No `Content-Security-Policy` is sent (`config/initializers/content_security_policy.rb` is commented
 * out, U-813 preserved by D-012).
 */
class RailsResponseHeadersFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        DEFAULT_HEADERS.forEach { (name, value) -> response.setHeader(name, value) }
        chain.doFilter(request, CacheControlResponse(response))
    }

    /** Keeps a default `Cache-Control` in line with the status until the handler sets its own. */
    private class CacheControlResponse(
        response: HttpServletResponse,
    ) : HttpServletResponseWrapper(response) {
        private var defaulted = true

        init {
            applyDefault(response.status)
        }

        override fun setStatus(sc: Int) {
            super.setStatus(sc)
            applyDefault(sc)
        }

        override fun sendError(sc: Int) {
            applyDefault(sc)
            super.sendError(sc)
        }

        override fun sendError(
            sc: Int,
            msg: String?,
        ) {
            applyDefault(sc)
            super.sendError(sc, msg)
        }

        override fun sendRedirect(location: String) {
            applyDefault(HttpServletResponse.SC_FOUND)
            super.sendRedirect(location)
        }

        override fun setHeader(
            name: String,
            value: String?,
        ) {
            if (name.equals(HttpHeaders.CACHE_CONTROL, ignoreCase = true)) defaulted = false
            super.setHeader(name, value)
        }

        override fun addHeader(
            name: String,
            value: String?,
        ) {
            if (name.equals(HttpHeaders.CACHE_CONTROL, ignoreCase = true)) defaulted = false
            super.addHeader(name, value)
        }

        override fun reset() {
            super.reset()
            DEFAULT_HEADERS.forEach { (name, value) -> super.setHeader(name, value) }
            defaulted = true
            applyDefault(status)
        }

        private fun applyDefault(status: Int) {
            if (!defaulted || isCommitted) return
            super.setHeader(HttpHeaders.CACHE_CONTROL, if (status in ETAG_STATUSES) REVALIDATE else HtmlErrorPages.NO_CACHE)
        }
    }

    companion object {
        const val REVALIDATE = "max-age=0, private, must-revalidate"
        private val ETAG_STATUSES = setOf(HttpServletResponse.SC_OK, HttpServletResponse.SC_CREATED)

        val DEFAULT_HEADERS: Map<String, String> =
            linkedMapOf(
                "X-Frame-Options" to "SAMEORIGIN",
                "X-XSS-Protection" to "0",
                "X-Content-Type-Options" to "nosniff",
                "X-Permitted-Cross-Domain-Policies" to "none",
                "Referrer-Policy" to "strict-origin-when-cross-origin",
            )
    }
}
