package com.softberries.openbanking.config

import com.softberries.openbanking.health.web.UpController
import com.softberries.openbanking.web.WebSecurityConventions
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Filter chains, most specific first. Servlet filters in front of them (CORS, host check, SSL, headers, `_method`, browser
 * gate) are registered in [com.softberries.openbanking.web.WebConfiguration]. Later units add chains between [HEALTH_ORDER]
 * and [FALLBACK_ORDER] and start each with [WebSecurityConventions.apply].
 */
// Not loaded for `db:migrate` (no web server).
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {
    /** `/up` is a liveness probe: public, stateless, no CSRF, no request cache (U-522). */
    @Bean
    @Order(HEALTH_ORDER)
    fun healthCheckFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(UpController.PATH)
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .securityContext { it.disable() }
            .csrf { it.disable() }
            .headers { it.disable() }
            .logout { it.disable() }
            .build()

    /**
     * Everything else: Rails conventions (CSRF, public router redirects, unrouted → 404). Routed requests stay closed until
     * the authentication units (M-006, M-008, M-015) add their chains.
     */
    @Bean
    @Order(FALLBACK_ORDER)
    fun defaultFilterChain(
        http: HttpSecurity,
        conventions: WebSecurityConventions,
    ): SecurityFilterChain =
        conventions
            .apply(http)
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()

    companion object {
        const val HEALTH_ORDER = 0
        const val FALLBACK_ORDER = Int.MAX_VALUE
    }
}
