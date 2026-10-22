package com.softberries.openbanking.web

import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class TransportSecurityFilterTest {
    private val setsCookie =
        MockFilterChain(
            object : jakarta.servlet.http.HttpServlet() {
                override fun service(
                    req: jakarta.servlet.ServletRequest,
                    res: jakarta.servlet.ServletResponse,
                ) {
                    (res as HttpServletResponse).addCookie(Cookie("remember_user_token", "x"))
                }
            },
        )

    @Test
    fun `with assume_ssl every request counts as HTTPS - HSTS and Secure cookies, no redirect`() {
        val request = MockHttpServletRequest("GET", "/admin").apply { serverName = "finance.example.com" }
        val response = MockHttpServletResponse()

        TransportSecurityFilter().doFilter(request, response, setsCookie)

        response.status shouldBe 200
        response.getHeader("Strict-Transport-Security") shouldBe "max-age=63072000; includeSubDomains"
        response.getCookie("remember_user_token")!!.secure shouldBe true
    }

    @Test
    fun `a plain HTTP request without assume_ssl is redirected to HTTPS, GET with 301 and other verbs with 308`() {
        val get = MockHttpServletRequest("GET", "/admin/analytics").apply { serverName = "finance.example.com" }
        get.queryString = "from=2026-01-01"
        val getResponse = MockHttpServletResponse()
        TransportSecurityFilter(assumeSsl = false).doFilter(get, getResponse, MockFilterChain())

        getResponse.status shouldBe 301
        getResponse.getHeader("Location") shouldBe "https://finance.example.com/admin/analytics?from=2026-01-01"

        val post = MockHttpServletRequest("POST", "/admin/categories").apply { serverName = "finance.example.com" }
        val postResponse = MockHttpServletResponse()
        TransportSecurityFilter(assumeSsl = false).doFilter(post, postResponse, MockFilterChain())
        postResponse.status shouldBe 308
    }

    @Test
    fun `up is never redirected`() {
        val response = MockHttpServletResponse()

        TransportSecurityFilter(assumeSsl = false).doFilter(MockHttpServletRequest("GET", "/up"), response, MockFilterChain())

        response.status shouldBe 200
    }
}
