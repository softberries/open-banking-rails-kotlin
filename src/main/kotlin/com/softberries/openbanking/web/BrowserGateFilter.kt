package com.softberries.openbanking.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `allow_browser versions: :modern` (C1): a legacy browser on an HTML route gets the static 406 page. It runs before Spring
 * Security, so it precedes the first-run redirect and authentication, as the Rails callback does.
 * Requests without a route keep their 404, as in Rails (the check lives in the controller).
 */
class BrowserGateFilter(
    private val routes: ApplicationRoutes,
    private val pages: HtmlErrorPages,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = WebPaths.isNonHtml(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (ModernBrowser.isBlocked(request.getHeader(HttpHeaders.USER_AGENT)) && isGated(request)) {
            pages.renderUnsupportedBrowser(response)
            return
        }
        chain.doFilter(request, response)
    }

    private fun isGated(request: HttpServletRequest): Boolean {
        val handler = routes.handlerFor(request) ?: return false
        return !AnnotatedElementUtils.hasAnnotation(handler.beanType, SkipBrowserGate::class.java)
    }
}
