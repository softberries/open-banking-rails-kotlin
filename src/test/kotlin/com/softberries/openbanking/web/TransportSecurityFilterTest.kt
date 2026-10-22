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
    fun `there is no HTTPS redirect - assume_ssl makes even the liveness probe count as HTTPS`() {
        val request = MockHttpServletRequest("GET", "/up").apply { serverName = "finance.example.com" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        TransportSecurityFilter().doFilter(request, response, chain)

        response.status shouldBe 200
        chain.request!!.isSecure shouldBe true
        chain.request!!.scheme shouldBe "https"
    }
}
