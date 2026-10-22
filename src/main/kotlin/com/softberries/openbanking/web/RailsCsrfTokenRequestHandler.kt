package com.softberries.openbanking.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestHandler
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler
import java.util.function.Supplier

/**
 * Spring's masked CSRF token handling plus Rails' `valid_request_origin?` (`forgery_protection_origin_check`, on since
 * `load_defaults 5.0`, `config/application.rb:33`): a request whose `Origin` is `null` or differs from the base URL is rejected
 * like a bad token (→ 422), even when the token itself is valid. A request without `Origin` is accepted. The base URL honours the
 * reverse-proxy headers as Rails does ([RailsRequestUrl]).
 */
class RailsCsrfTokenRequestHandler(
    private val delegate: CsrfTokenRequestHandler = XorCsrfTokenRequestAttributeHandler(),
) : CsrfTokenRequestHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        csrfToken: Supplier<CsrfToken>,
    ) = delegate.handle(request, response, csrfToken)

    override fun resolveCsrfTokenValue(
        request: HttpServletRequest,
        csrfToken: CsrfToken,
    ): String? = if (validOrigin(request)) delegate.resolveCsrfTokenValue(request, csrfToken) else null

    private fun validOrigin(request: HttpServletRequest): Boolean {
        val origin = request.getHeader(HttpHeaders.ORIGIN) ?: return true
        return origin == RailsRequestUrl.baseUrl(request)
    }
}
