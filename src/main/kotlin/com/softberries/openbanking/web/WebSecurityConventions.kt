package com.softberries.openbanking.web

import jakarta.servlet.DispatcherType
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.access.DelegatingAccessDeniedHandler
import org.springframework.security.web.csrf.CsrfException
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.security.web.util.matcher.AndRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.stereotype.Component

/**
 * The Rails conventions every HTML [org.springframework.security.web.SecurityFilterChain] applies first; later units (M-006
 * `/admin` pages, M-008 `/setup` + first-run, M-015 JSON API + `/mcp`) call [apply] and then add their own URL rules:
 *
 * - CSRF (C5): Rails parameter names (`authenticity_token` form field, `X-CSRF-Token` header, templates read the token
 *   from the `_csrf` request attribute for `<meta name="csrf-token">`); checked for non-GET requests that reach a
 *   controller, never for the JSON API (`/api/`) or `/mcp`; a foreign `Origin` fails like a bad token
 *   ([RailsCsrfTokenRequestHandler]); failure → 422 page (Spring's default would be 403).
 * - Requests without a route and error dispatches are permitted, so they keep their 404 / error page for everybody (X-16);
 *   the router redirects `/` and `/admin` are public.
 * - Response headers come from [RailsResponseHeadersFilter] and [TransportSecurityFilter], so Spring Security's own header
 *   writers (which would send `no-store` and `X-Frame-Options: DENY`) are off; `/logout` is not a Rails route.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class WebSecurityConventions(
    private val routes: ApplicationRoutes,
) {
    val nonHtmlPaths: RequestMatcher = RequestMatcher { WebPaths.isNonHtml(it) }

    val csrfProtected: RequestMatcher =
        AndRequestMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER, NegatedRequestMatcher(nonHtmlPaths), routes.routed)

    val accessDeniedHandler: AccessDeniedHandler =
        DelegatingAccessDeniedHandler(
            linkedMapOf(CsrfException::class.java to AccessDeniedHandler { _, response, _ -> response.sendError(UNPROCESSABLE) }),
            AccessDeniedHandlerImpl(),
        )

    fun apply(http: HttpSecurity): HttpSecurity =
        http
            .csrf { csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository())
                csrf.csrfTokenRequestHandler(RailsCsrfTokenRequestHandler())
                csrf.requireCsrfProtectionMatcher(csrfProtected)
            }.exceptionHandling { it.accessDeniedHandler(accessDeniedHandler) }
            .headers { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests { auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                auth.requestMatchers(routes.unrouted).permitAll()
                auth.requestMatchers(HttpMethod.GET, *RouterRedirectsController.PATHS).permitAll()
                auth.requestMatchers(HttpMethod.HEAD, *RouterRedirectsController.PATHS).permitAll()
            }

    companion object {
        const val CSRF_PARAMETER = "authenticity_token"
        const val CSRF_HEADER = "X-CSRF-Token"
        private const val UNPROCESSABLE = 422

        fun csrfTokenRepository(): HttpSessionCsrfTokenRepository =
            HttpSessionCsrfTokenRepository().apply {
                setParameterName(CSRF_PARAMETER)
                setHeaderName(CSRF_HEADER)
            }
    }
}
