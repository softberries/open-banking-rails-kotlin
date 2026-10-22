package com.softberries.openbanking.web

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/** `request.base_url` of ActionDispatch / Rack behind a reverse proxy. */
class RailsRequestUrlTest {
    private fun request(vararg headers: Pair<String, String>) =
        MockHttpServletRequest("POST", "/admin/categories").apply {
            serverName = "app"
            serverPort = 3000
            headers.forEach { (name, value) -> addHeader(name, value) }
        }

    @Test
    fun `host and port come from the Host header, standard ports are dropped`() {
        RailsRequestUrl.baseUrl(request("Host" to "localhost:3000")) shouldBe "http://localhost:3000"
        RailsRequestUrl.baseUrl(request("Host" to "finance.example.com:80")) shouldBe "http://finance.example.com"
        RailsRequestUrl.baseUrl(request("Host" to "finance.example.com")) shouldBe "http://finance.example.com"
    }

    @Test
    fun `without Host the server name and port are used`() {
        RailsRequestUrl.baseUrl(request()) shouldBe "http://app:3000"
    }

    @Test
    fun `the last X-Forwarded-Host wins over Host`() {
        RailsRequestUrl.baseUrl(request("Host" to "app:3000", "X-Forwarded-Host" to "edge.example, finance.example.com")) shouldBe
            "http://finance.example.com"
    }

    @Test
    fun `X-Forwarded-Proto from a TLS proxy makes the base URL https`() {
        val proxied = request("Host" to "finance.example.com", "X-Forwarded-Proto" to "https")

        RailsRequestUrl.baseUrl(proxied) shouldBe "https://finance.example.com"
        RailsRequestUrl.baseUrl(request("Host" to "finance.example.com:443", "X-Forwarded-Proto" to "http, https")) shouldBe
            "https://finance.example.com"
        RailsRequestUrl.baseUrl(request("Host" to "finance.example.com", "X-Forwarded-Proto" to "https, bogus")) shouldBe
            "https://finance.example.com"
    }

    @Test
    fun `Forwarded proto, X-Forwarded-Ssl and X-Forwarded-Scheme are honoured like Rack`() {
        RailsRequestUrl.scheme(request("Forwarded" to "for=1.2.3.4;proto=https")) shouldBe "https"
        RailsRequestUrl.scheme(request("X-Forwarded-Ssl" to "on")) shouldBe "https"
        RailsRequestUrl.scheme(request("X-Forwarded-Scheme" to "https")) shouldBe "https"
        RailsRequestUrl.scheme(request("Forwarded" to "proto=HTTPS", "X-Forwarded-Proto" to "http")) shouldBe "http"
        RailsRequestUrl.scheme(request()) shouldBe "http"
    }

    @Test
    fun `a secure request (TLS or assume_ssl) is https whatever the headers say`() {
        val secure = request("Host" to "finance.example.com", "X-Forwarded-Proto" to "http").apply { isSecure = true }

        RailsRequestUrl.baseUrl(secure) shouldBe "https://finance.example.com"
    }
}
