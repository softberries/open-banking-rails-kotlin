package com.softberries.openbanking.web

import com.softberries.openbanking.support.IntegrationTest
import com.softberries.openbanking.support.Oracle
import com.softberries.openbanking.web.WebClient.Companion.title
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType

@Import(WebTestEndpoints::class, WebTestEndpoints.Endpoints::class)
class WebConventionsTest : IntegrationTest() {
    @LocalServerPort
    private var port: Int = 0

    private lateinit var web: WebClient

    @BeforeEach
    fun client() {
        web = WebClient(port)
    }

    @Nested
    inner class RouterRedirects {
        @Test
        fun `GET root matches the oracle GET_root default case`() {
            val expected = Oracle.case("GET_root.json", "default").path("response")

            val response = web.get("/")

            response.statusCode() shouldBe expected.path("status").asInt()
            response.headers().firstValue("location").get() shouldBe "http://127.0.0.1:$port/admin"
            sameMediaType(response.headers().firstValue("content-type").get(), expected.path("headers").path("content-type").asText())
            response.headers().firstValue("cache-control").get() shouldBe expected.path("headers").path("cache-control").asText()
            response.headers().firstValue("content-length").get() shouldBe "0"
            response.body() shouldBe ""
        }

        @Test
        fun `GET admin matches the oracle GET_admin default case for anonymous users`() {
            val expected = Oracle.case("GET_admin.json", "default").path("response")

            val response = web.get("/admin")

            response.statusCode() shouldBe expected.path("status").asInt()
            response.headers().firstValue("location").get() shouldBe "http://127.0.0.1:$port/admin/analytics"
            sameMediaType(response.headers().firstValue("content-type").get(), expected.path("headers").path("content-type").asText())
            response.body() shouldBe ""
        }

        @Test
        fun `first_run_setup_spec 16 - on an empty database the root redirects to admin before any first-run check`() {
            jdbc.queryForObject("SELECT count(*) FROM users", Long::class.java) shouldBe 0L

            web
                .get("/")
                .headers()
                .firstValue("location")
                .get() shouldBe "http://127.0.0.1:$port/admin"
            web.get("/admin").statusCode() shouldBe 301
        }

        @Test
        fun `auth_spec 12 - with an existing user an anonymous GET admin is still the 301 to analytics`() {
            jdbc.update(
                "INSERT INTO users (email, encrypted_password, name, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
                "existing@example.test",
                "x",
                "Existing",
            )

            val response = web.get("/admin")

            response.statusCode() shouldBe 301
            response.headers().firstValue("location").get() shouldBe "http://127.0.0.1:$port/admin/analytics"
        }

        @Test
        fun `redirects are not subject to the browser gate`() {
            web.get("/", mapOf("User-Agent" to IE11)).statusCode() shouldBe 301
        }
    }

    @Nested
    inner class Csrf {
        @Test
        fun `POST without a token gets the static 422 page (oracle POST_admin_categories missing_csrf_token)`() {
            val expected = Oracle.case("POST_admin_categories.json", "missing_csrf_token").path("response")

            val response = web.send("POST", "/test/html/form", form = mapOf("category[name]" to "CSRF probe"))

            response.statusCode() shouldBe expected.path("status").asInt()
            title(response.body()) shouldBe "The change you wanted was rejected (422 Unprocessable Entity)"
            response.body() shouldContain "Maybe you tried to change something you didn't have access to."
        }

        @Test
        fun `POST with a wrong token in an existing session gets 422`() {
            val (cookie, _) = web.csrfSession()

            val response = web.send("POST", "/test/html/form", mapOf("Cookie" to cookie), mapOf("authenticity_token" to "forged"))

            response.statusCode() shouldBe 422
        }

        @Test
        fun `token is accepted from the authenticity_token form field`() {
            val (cookie, token) = web.csrfSession()

            val response = web.send("POST", "/test/html/form", mapOf("Cookie" to cookie), mapOf("authenticity_token" to token))

            response.statusCode() shouldBe 200
            response.body() shouldBe "created:POST"
        }

        @Test
        fun `token is accepted from the X-CSRF-Token header`() {
            val (cookie, token) = web.csrfSession()

            val response = web.send("POST", "/test/html/form", mapOf("Cookie" to cookie, "X-CSRF-Token" to token), mapOf())

            response.statusCode() shouldBe 200
        }

        @Test
        fun `CSRF does not apply to the JSON API or MCP`() {
            web.send("POST", "/api/test/echo").body() shouldBe "api"
            web.send("POST", "/mcp").statusCode() shouldBe 404
        }
    }

    @Nested
    inner class MethodOverride {
        @ParameterizedTest
        @ValueSource(strings = ["patch", "PATCH", "Patch", "put", "PUT"])
        fun `POST with _method is dispatched as that verb and PATCH and PUT reach the same handler`(method: String) {
            val (cookie, token) = web.csrfSession()

            val response =
                web.send("POST", "/test/html/thing", mapOf("Cookie" to cookie), mapOf("_method" to method, "authenticity_token" to token))

            response.statusCode() shouldBe 200
            response.body() shouldBe "updated:${method.uppercase()}"
        }

        @Test
        fun `POST with _method=delete reaches the DELETE handler`() {
            val (cookie, token) = web.csrfSession()

            val response =
                web.send("POST", "/test/html/thing", mapOf("Cookie" to cookie), mapOf("_method" to "delete", "authenticity_token" to token))

            response.body() shouldBe "deleted"
        }

        @Test
        fun `overridden requests are still CSRF-checked`() {
            web.send("POST", "/test/html/thing", form = mapOf("_method" to "patch")).statusCode() shouldBe 422
        }

        @Test
        fun `real PATCH and PUT reach the same handler`() {
            val (cookie, token) = web.csrfSession()
            val headers = mapOf("Cookie" to cookie, "X-CSRF-Token" to token)

            web.send("PATCH", "/test/html/thing", headers, mapOf()).body() shouldBe "updated:PATCH"
            web.send("PUT", "/test/html/thing", headers, mapOf()).body() shouldBe "updated:PUT"
        }
    }

    @Nested
    inner class BrowserGate {
        @Test
        fun `legacy browser gets the 406 page (oracle GET_admin_analytics legacy_browser_ua)`() {
            val expected = Oracle.case("GET_admin_analytics.json", "legacy_browser_ua").path("response")

            val response = web.get("/test/html", mapOf("User-Agent" to IE11))

            response.statusCode() shouldBe expected.path("status").asInt()
            title(response.body()) shouldBe expected.path("body_sample").path("html_title").asText()
            response.body() shouldContain "<strong>Your browser is not supported.</strong><br> Please upgrade your browser to continue."
            response.body().toByteArray().size shouldBe expected.path("body_sample").path("length").asInt()
            response.headers().firstValue("x-frame-options").get() shouldBe "SAMEORIGIN"
            response.headers().firstValue("x-content-type-options").get() shouldBe "nosniff"
        }

        @Test
        fun `modern browser and a request without User-Agent pass`() {
            web.get("/test/html", mapOf("User-Agent" to CHROME_120)).statusCode() shouldBe 200
            web.get("/test/html").statusCode() shouldBe 200
        }

        @Test
        fun `gate runs before authentication - an anonymous legacy browser gets 406 not the auth response`() {
            web.get("/test/protected").statusCode() shouldBe 403
            web.get("/test/protected", mapOf("User-Agent" to IE11)).statusCode() shouldBe 406
        }

        @Test
        fun `not applied to up, the API, MCP or unrouted paths`() {
            web.get("/up", mapOf("User-Agent" to IE11)).statusCode() shouldBe 200
            web.send("POST", "/api/test/echo", mapOf("User-Agent" to IE11)).statusCode() shouldBe 200
            web.get("/mcp", mapOf("User-Agent" to IE11)).statusCode() shouldBe 404
            web.get("/admin/does-not-exist", mapOf("User-Agent" to IE11)).statusCode() shouldBe 404
        }
    }

    @Nested
    inner class ErrorPages {
        @Test
        fun `unhandled exception gives the 500 page without any detail`() {
            val response = web.get("/test/html/boom")

            response.statusCode() shouldBe 500
            title(response.body()) shouldBe "We're sorry, but something went wrong (500 Internal Server Error)"
            response.body() shouldNotContain "secret failure detail"
            response.body() shouldNotContain "IllegalStateException"
            response.body() shouldNotContain "at com.softberries"
        }

        @Test
        fun `missing required form key gives the 400 page`() {
            val response = web.get("/test/html/missing")

            response.statusCode() shouldBe 400
            title(response.body()) shouldBe "The server cannot process the request due to a client error (400 Bad Request)"
        }

        @Test
        fun `RecordNotFoundException gives the 404 page`() {
            val response = web.get("/test/html/not-found")

            response.statusCode() shouldBe 404
            title(response.body()) shouldBe "The page you were looking for doesn't exist (404 Not found)"
            response.headers().firstValue("content-type").get() shouldStartWith "text/html"
            response.headers().firstValue("cache-control").get() shouldBe "no-cache"
        }

        @Test
        fun `explicit JSON request gets the PublicExceptions JSON body`() {
            val response = web.get("/test/html/not-found", mapOf("Accept" to "application/json"))

            response.statusCode() shouldBe 404
            response.body() shouldBe """{"status":404,"error":"Not Found"}"""
            response.headers().firstValue("content-type").get() shouldStartWith "application/json"
            web.get("/admin/nope", mapOf("Accept" to "application/json")).body() shouldBe """{"status":404,"error":"Not Found"}"""
        }

        @Test
        fun `a browser Accept header still gets the page`() {
            val response = web.get("/test/html/not-found", mapOf("Accept" to "text/html,application/json;q=0.9,*/*;q=0.8"))

            title(response.body()) shouldBe "The page you were looking for doesn't exist (404 Not found)"
        }

        @Test
        fun `a known HTML path with another verb is a routing error - 404 page, no Allow header, no CSRF check`() {
            val response = web.send("DELETE", "/test/html/form")

            response.statusCode() shouldBe 404
            title(response.body()) shouldBe "The page you were looking for doesn't exist (404 Not found)"
            response.headers().firstValue("allow").isPresent shouldBe false
            web.get("/test/html/form").statusCode() shouldBe 404
        }

        @Test
        fun `the JSON API keeps 405 for another verb`() {
            web.get("/api/test/echo").statusCode() shouldBe 405
        }

        @Test
        fun `a 406 that is not the browser gate has an empty body`() {
            val response = web.get("/test/html", mapOf("Accept" to "text/plain"))

            response.statusCode() shouldBe 406
            response.body() shouldBe ""
        }

        @Test
        fun `unmatched API paths answer 404`() {
            web.get("/api/v1/does-not-exist").statusCode() shouldBe 404
            web.send("DELETE", "/api/v1/merchants/1/nope").statusCode() shouldBe 404
        }

        @Test
        fun `the error path itself is not a route`() {
            web.get("/error").statusCode() shouldBe 404
        }

        @Test
        fun `Spring's logout endpoint does not exist`() {
            web.send("POST", "/logout").statusCode() shouldBe 404
        }
    }

    @Nested
    inner class Cors {
        @Test
        fun `preflight matches the oracle OPTIONS_api__version_merchants cors_preflight case`() {
            val expected = Oracle.case("OPTIONS_api__version_merchants.json", "cors_preflight")
            val headers = expected.path("request").path("headers")

            val response =
                web.send(
                    "OPTIONS",
                    "/api/v1/merchants",
                    mapOf(
                        "Origin" to headers.path("origin").asText(),
                        "Access-Control-Request-Method" to headers.path("access-control-request-method").asText(),
                        "Access-Control-Request-Headers" to headers.path("access-control-request-headers").asText(),
                    ),
                )

            val want = expected.path("response")
            response.statusCode() shouldBe want.path("status").asInt()
            response.body() shouldBe ""
            response.headers().firstValue("access-control-allow-origin").get() shouldBe "*"
            normalise(response.headers().firstValue("access-control-allow-methods").get()) shouldBe
                normalise(want.path("headers").path("access-control-allow-methods").asText())
            response.headers().firstValue("access-control-allow-credentials").isPresent shouldBe false
        }

        @Test
        fun `cross-origin requests on HTML routes get a wildcard origin and no credentials flag`() {
            val response = web.get("/test/html", mapOf("Origin" to "https://evil.example"))

            response.headers().firstValue("access-control-allow-origin").get() shouldBe "*"
            response.headers().allValues("access-control-allow-credentials").shouldBeEmpty()
        }

        @ParameterizedTest
        @ValueSource(strings = ["/", "/test/html", "/up", "/admin/does-not-exist", "/api/v1/x"])
        fun `every response varies on Origin`(path: String) {
            val vary =
                web
                    .get(path)
                    .headers()
                    .allValues("vary")
                    .joinToString(",")

            vary shouldContain "Origin"
        }

        private fun normalise(value: String) = value.split(",").map { it.trim() }
    }

    @Nested
    inner class DefaultHeaders {
        @Test
        fun `dynamic responses carry the Rails default headers and no CSP`() {
            val response = web.get("/test/html")

            response.statusCode() shouldBe 200
            val headers = response.headers()
            headers.firstValue("x-frame-options").get() shouldBe "SAMEORIGIN"
            headers.firstValue("x-content-type-options").get() shouldBe "nosniff"
            headers.firstValue("referrer-policy").get() shouldBe "strict-origin-when-cross-origin"
            headers.firstValue("x-permitted-cross-domain-policies").get() shouldBe "none"
            headers.allValues("cache-control") shouldBe listOf("max-age=0, private, must-revalidate")
            headers.firstValue("content-security-policy").isPresent shouldBe false
            headers.firstValue("strict-transport-security").isPresent shouldBe false
        }

        @Test
        fun `session cookie has the Rails name and attributes and no Secure flag outside production`() {
            val cookie =
                web
                    .get("/test/html/csrf")
                    .headers()
                    .firstValue("set-cookie")
                    .get()

            cookie shouldStartWith "_open_banking_rails_session="
            val attributes = cookie.split(";").map { it.trim().lowercase() }
            attributes shouldContainAll listOf("path=/", "httponly", "samesite=lax")
            attributes.none { it == "secure" } shouldBe true
        }

        @Test
        fun `no session is created for anonymous page views`() {
            web
                .get("/test/html")
                .headers()
                .allValues("set-cookie")
                .shouldBeEmpty()
            web
                .get("/")
                .headers()
                .allValues("set-cookie")
                .shouldBeEmpty()
        }
    }

    @Nested
    inner class UnportedRoutes {
        @ParameterizedTest(name = "{0}")
        @UnportedRoutesSource
        fun `answers 404 with the 404 page for anonymous users (X-16)`(
            method: String,
            path: String,
        ) {
            val response = web.send(method, path, form = if (method == "GET") null else mapOf("probe" to "1"))

            response.statusCode() shouldBe 404
            title(response.body()) shouldBe "The page you were looking for doesn't exist (404 Not found)"
        }

        @Test
        fun `the superseded oracle samples did not answer 404 for every route`() {
            val superseded =
                listOf(
                    "GET_admin_debug.json" to 200,
                    "GET_admin_debug_runtime_error.json" to 500,
                    "GET_recede_historical_location.json" to 200,
                    "GET_rails_conductor_action_mailbox_inbound_emails.json" to 500,
                    "POST_admin_debug_enqueue_failing_job.json" to 204,
                )
            superseded.forEach { (file, status) ->
                Oracle
                    .case(file, "default")
                    .path("response")
                    .path("status")
                    .asInt() shouldBe status
                status shouldNotBe 404
            }
        }
    }

    /** Tomcat writes `text/html;charset=UTF-8` without the space Rack writes; the media type is identical. */
    private fun sameMediaType(
        actual: String,
        expected: String,
    ) {
        MediaType.parseMediaType(actual) shouldBe MediaType.parseMediaType(expected)
    }

    companion object {
        const val IE11 = "Mozilla/5.0 (Windows NT 10.0; Trident/7.0; rv:11.0) like Gecko"
        const val CHROME_120 =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

private infix fun List<String>.shouldContainAll(expected: List<String>) {
    expected.forEach { (it in this) shouldBe true }
}
