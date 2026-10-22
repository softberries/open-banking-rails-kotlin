package com.softberries.openbanking.config

import com.softberries.openbanking.health.web.UpController
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

// Not loaded for `db:migrate` (no web server).
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {
    /** `/up` is a liveness probe: public, stateless, no CSRF, no request cache (U-522). */
    @Bean
    @Order(0)
    fun healthCheckFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(UpController.PATH)
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .securityContext { it.disable() }
            .csrf { it.disable() }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() }
                headers.cacheControl { it.disable() }
            }.build()

    /** Everything else is closed until the authentication units (M-002, M-006) add their own chains. */
    @Bean
    @Order(Int.MAX_VALUE)
    fun defaultFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()
}
