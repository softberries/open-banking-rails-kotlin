package com.softberries.openbanking.web

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class HostAuthorizationFilterTest {
    private fun status(
        hosts: List<String>,
        host: String,
        path: String = "/admin",
        forwarded: String? = null,
    ): Int {
        val request = MockHttpServletRequest("GET", path).apply { addHeader("Host", host) }
        forwarded?.let { request.addHeader("X-Forwarded-Host", it) }
        val response = MockHttpServletResponse()
        HostAuthorizationFilter(hosts).doFilter(request, response, MockFilterChain())
        return response.status
    }

    @Test
    fun `empty list allows every host`() {
        status(emptyList(), "anything.example") shouldBe 200
    }

    @Test
    fun `exact host matches case-insensitively with any port`() {
        status(listOf("finance.example.com"), "FINANCE.example.com") shouldBe 200
        status(listOf("finance.example.com"), "finance.example.com:3000") shouldBe 200
        status(listOf("finance.example.com"), "sub.finance.example.com") shouldBe 403
    }

    @Test
    fun `an entry with a port only matches that port`() {
        status(listOf("localhost:3000"), "localhost:3000") shouldBe 200
        status(listOf("localhost:3000"), "localhost:4000") shouldBe 403
    }

    @Test
    fun `leading dot allows the domain and its subdomains`() {
        status(listOf(".example.org"), "example.org") shouldBe 200
        status(listOf(".example.org"), "a.b.example.org") shouldBe 200
        status(listOf(".example.org"), "badexample.org") shouldBe 403
    }

    @Test
    fun `the last forwarded host must be allowed as well`() {
        status(listOf("a.test"), "a.test", forwarded = "evil.test, a.test") shouldBe 200
        status(listOf("a.test"), "a.test", forwarded = "a.test, evil.test") shouldBe 403
    }

    @Test
    fun `up is exempt`() {
        status(listOf("a.test"), "evil.test", path = "/up") shouldBe 200
    }
}
