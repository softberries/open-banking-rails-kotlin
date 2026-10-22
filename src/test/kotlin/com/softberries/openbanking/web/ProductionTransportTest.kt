package com.softberries.openbanking.web

import com.softberries.openbanking.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * `config/environments/production.rb:27-31,95-103` with `FORCE_SSL=true` and an `APP_HOSTS` list. (`app.hosts` is what the
 * `APP_HOSTS` environment variable binds to; relaxed binding of upper-case names only applies to real environment variables.)
 */
@ActiveProfiles("production")
@AutoConfigureMockMvc
@Import(WebTestEndpoints::class, WebTestEndpoints.Endpoints::class)
@TestPropertySource(
    properties = [
        "FORCE_SSL=true",
        "app.hosts=finance.example.com, .example.org,127.0.0.1",
        "app.host=ignored.example.net",
        "SECRET_KEY_BASE=test-secret",
    ],
)
class ProductionTransportTest : IntegrationTest() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var mvc: MockMvc

    private lateinit var web: WebClient

    @BeforeEach
    fun client() {
        web = WebClient(port)
    }

    @Nested
    inner class ForceSsl {
        @Test
        fun `requests are treated as HTTPS behind the proxy - absolute URLs use https and no redirect loop`() {
            val response = web.get("/")

            response.statusCode() shouldBe 301
            response.headers().firstValue("location").get() shouldBe "https://127.0.0.1/admin"
        }

        @Test
        fun `HSTS is sent`() {
            web
                .get("/test/html")
                .headers()
                .firstValue("strict-transport-security")
                .get() shouldBe TransportSecurityFilter.HSTS_VALUE
        }

        @Test
        fun `the session cookie is Secure`() {
            val cookie =
                web
                    .get("/test/html/csrf")
                    .headers()
                    .firstValue("set-cookie")
                    .get()

            cookie.split(";").map { it.trim().lowercase() }.contains("secure") shouldBe true
        }

        @Test
        fun `up still answers 200`() {
            web.get("/up").statusCode() shouldBe 200
        }
    }

    @Nested
    inner class HostAuthorization {
        @Test
        fun `a listed host passes, with any port`() {
            mvc.get("/test/html") { header("Host", "finance.example.com:8443") }.andExpect { status { isOk() } }
        }

        @Test
        fun `a leading dot allows subdomains`() {
            mvc.get("/test/html") { header("Host", "app.example.org") }.andExpect { status { isOk() } }
        }

        @Test
        fun `an unlisted host gets an empty 403 - APP_HOST is ignored when APP_HOSTS is set`() {
            val result =
                mvc
                    .get("/test/html") { header("Host", "ignored.example.net") }
                    .andExpect { status { isForbidden() } }
                    .andReturn()

            result.response.contentAsString shouldBe ""
            result.response.contentType!! shouldContain "text/html"
        }

        @Test
        fun `a forwarded host outside the list is blocked too`() {
            mvc
                .get("/test/html") {
                    header("Host", "finance.example.com")
                    header("X-Forwarded-Host", "evil.example")
                }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `up is exempt`() {
            mvc.get("/up") { header("Host", "evil.example") }.andExpect { status { isOk() } }
        }
    }
}

/** `FORCE_SSL` set to anything but "true" in production disables the HTTPS handling; an empty `APP_HOSTS` disables the host check. */
@ActiveProfiles("production")
@AutoConfigureMockMvc
@Import(WebTestEndpoints::class, WebTestEndpoints.Endpoints::class)
@TestPropertySource(properties = ["FORCE_SSL=TRUE", "app.hosts=", "app.host=finance.example.com", "SECRET_KEY_BASE=test-secret"])
class ProductionTransportDisabledTest : IntegrationTest() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `no HTTPS redirect, no HSTS, no Secure cookie, http Location`() {
        val web = WebClient(port)

        val redirect = web.get("/")
        redirect.headers().firstValue("location").get() shouldBe "http://127.0.0.1:$port/admin"
        redirect.headers().firstValue("strict-transport-security").isPresent shouldBe false
        val cookie =
            web
                .get("/test/html/csrf")
                .headers()
                .firstValue("set-cookie")
                .get()
        cookie.split(";").map { it.trim().lowercase() }.contains("secure") shouldBe false
    }

    @Test
    fun `an empty APP_HOSTS disables the check even when APP_HOST is set`() {
        mvc.get("/test/html") { header("Host", "anything.example") }.andExpect { status { isOk() } }
    }
}
