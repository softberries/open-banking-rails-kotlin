package com.softberries.openbanking.web

import com.softberries.openbanking.config.AppProperties
import jakarta.servlet.Filter
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.server.Cookie
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.filter.OrderedHiddenHttpMethodFilter
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory
import org.springframework.boot.web.servlet.server.Session
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

/**
 * The Rack middleware stack in front of every controller, in Rails order:
 * CORS (`Rack::Cors`, inserted at 0) → host authorization → assume SSL / HSTS → default headers → `_method` override
 * (`Rack::MethodOverride`) → modern-browser gate → Spring Security (CSRF, first-run M-008, authentication M-006/M-008).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class WebConfiguration {
    @Bean
    fun webTransportSettings(
        properties: AppProperties,
        environment: Environment,
    ): WebTransportSettings = WebTransportSettings.from(properties, environment)

    /** `config/initializers/cors.rb`: any origin, any header, all methods, no credentials, on every path (D-012, U-812). */
    @Bean
    fun railsCorsFilter(): FilterRegistrationBean<CorsFilter> {
        val config =
            CorsConfiguration().apply {
                addAllowedOrigin(CorsConfiguration.ALL)
                addAllowedHeader(CorsConfiguration.ALL)
                allowedMethods = CORS_METHODS.map { it.name() }
                allowCredentials = false
                maxAge = CORS_MAX_AGE_SECONDS
            }
        val source = UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
        return registration(CorsFilter(source), FilterOrder.CORS)
    }

    @Bean
    fun hostAuthorizationFilter(settings: WebTransportSettings): FilterRegistrationBean<HostAuthorizationFilter> =
        registration(HostAuthorizationFilter(settings.allowedHosts), FilterOrder.HOST_AUTHORIZATION)

    @Bean
    fun transportSecurityFilter(settings: WebTransportSettings): FilterRegistrationBean<TransportSecurityFilter> =
        registration(TransportSecurityFilter(), FilterOrder.TRANSPORT_SECURITY).apply { isEnabled = settings.forceSsl }

    @Bean
    fun railsResponseHeadersFilter(): FilterRegistrationBean<RailsResponseHeadersFilter> =
        registration(RailsResponseHeadersFilter(), FilterOrder.RESPONSE_HEADERS)

    /** C6: `POST` + `_method=patch|put|delete` (any case) is dispatched as that verb, before Spring Security sees it. */
    @Bean
    fun hiddenHttpMethodFilter(): OrderedHiddenHttpMethodFilter =
        OrderedHiddenHttpMethodFilter().apply {
            order =
                FilterOrder.METHOD_OVERRIDE
        }

    @Bean
    fun browserGateFilter(
        routes: ApplicationRoutes,
        pages: HtmlErrorPages,
    ): FilterRegistrationBean<BrowserGateFilter> = registration(BrowserGateFilter(routes, pages), FilterOrder.BROWSER_GATE)

    /** C4: the Rails session cookie name and attributes; `Secure` only with `FORCE_SSL=true` in production. */
    @Bean
    fun sessionCookieCustomizer(settings: WebTransportSettings): WebServerFactoryCustomizer<AbstractServletWebServerFactory> =
        object : WebServerFactoryCustomizer<AbstractServletWebServerFactory>, Ordered {
            override fun customize(factory: AbstractServletWebServerFactory) {
                factory.session.trackingModes = setOf(Session.SessionTrackingMode.COOKIE)
                factory.session.cookie.apply {
                    name = SESSION_COOKIE
                    path = "/"
                    setHttpOnly(true)
                    sameSite = Cookie.SameSite.LAX
                    setSecure(settings.forceSsl)
                }
            }

            // After Spring Boot's ServletWebServerFactoryCustomizer (server.servlet.session.*).
            override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
        }

    private fun <T : Filter> registration(
        filter: T,
        order: Int,
    ): FilterRegistrationBean<T> = FilterRegistrationBean(filter).apply { this.order = order }

    /** Servlet filter order; Spring Security's filter chain runs at `SecurityProperties.DEFAULT_FILTER_ORDER` (-100). */
    object FilterOrder {
        const val CORS = Ordered.HIGHEST_PRECEDENCE + 1
        const val HOST_AUTHORIZATION = Ordered.HIGHEST_PRECEDENCE + 2
        const val TRANSPORT_SECURITY = Ordered.HIGHEST_PRECEDENCE + 3
        const val RESPONSE_HEADERS = Ordered.HIGHEST_PRECEDENCE + 4
        const val METHOD_OVERRIDE = OrderedHiddenHttpMethodFilter.DEFAULT_ORDER
        const val BROWSER_GATE = OrderedHiddenHttpMethodFilter.DEFAULT_ORDER + 1
    }

    companion object {
        const val SESSION_COOKIE = "_open_banking_rails_session"

        /** rack-cors `max_age` default. */
        const val CORS_MAX_AGE_SECONDS = 7200L

        val CORS_METHODS =
            listOf(
                HttpMethod.GET,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE,
                HttpMethod.OPTIONS,
                HttpMethod.HEAD,
            )
    }
}
