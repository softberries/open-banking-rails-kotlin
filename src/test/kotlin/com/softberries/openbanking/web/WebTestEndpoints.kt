package com.softberries.openbanking.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only HTML endpoints (until the real pages exist, e.g. M-069 for `/admin/analytics`) behind a public chain that applies
 * [WebSecurityConventions], as every later HTML chain does. `/test/protected` falls through to the closed fallback chain.
 */
@TestConfiguration(proxyBeanMethods = false)
class WebTestEndpoints {
    @Bean
    @Order(10)
    fun testHtmlFilterChain(
        http: HttpSecurity,
        conventions: WebSecurityConventions,
    ): SecurityFilterChain =
        conventions
            .apply(http.securityMatcher("/test/html/**", "/api/test/**"))
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    @RestController
    @Suppress("FunctionOnlyReturningConstant")
    class Endpoints {
        @GetMapping("/test/html", produces = [HTML])
        fun page() = "<!doctype html><title>ok</title>"

        @GetMapping("/test/protected", produces = [HTML])
        fun protectedPage() = "<!doctype html><title>secret</title>"

        @GetMapping("/test/html/csrf", produces = [MediaType.TEXT_PLAIN_VALUE])
        fun csrf(request: HttpServletRequest): String = (request.getAttribute(CsrfToken::class.java.name) as CsrfToken).token

        @PostMapping("/test/html/form", produces = [MediaType.TEXT_PLAIN_VALUE])
        fun create(request: HttpServletRequest) = "created:${request.method}"

        @RequestMapping("/test/html/thing", method = [RequestMethod.PATCH, RequestMethod.PUT], produces = [MediaType.TEXT_PLAIN_VALUE])
        fun update(request: HttpServletRequest) = "updated:${request.method}"

        @DeleteMapping("/test/html/thing", produces = [MediaType.TEXT_PLAIN_VALUE])
        fun destroy() = "deleted"

        @GetMapping("/test/html/boom")
        fun boom(): String = error("secret failure detail")

        @GetMapping("/test/html/missing")
        fun missing(): String = throw ParameterMissingException("category")

        @GetMapping("/test/html/not-found")
        fun notFound(): String = throw RecordNotFoundException()

        @PostMapping("/api/test/echo", produces = [MediaType.TEXT_PLAIN_VALUE])
        fun api() = "api"
    }

    companion object {
        const val HTML = "text/html;charset=UTF-8"
    }
}
