package com.softberries.openbanking.health.web

import com.softberries.openbanking.support.IntegrationTest
import com.softberries.openbanking.support.Oracle
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UpEndpointTest : IntegrationTest() {
    @LocalServerPort
    private var port: Int = 0

    private val client = HttpClient.newHttpClient()

    private fun get(vararg headers: String): HttpResponse<ByteArray> {
        val request =
            HttpRequest
                .newBuilder(URI("http://127.0.0.1:$port/up"))
                .apply { if (headers.isNotEmpty()) headers(*headers) }
                .GET()
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    @Test
    fun `matches the Ruby oracle GET_up default case on an empty database`() {
        jdbc.queryForObject("SELECT count(*) FROM users", Long::class.java) shouldBe 0L
        val expected = Oracle.case("GET_up.json", "default").path("response")

        val response = get()

        response.statusCode() shouldBe expected.path("status").asInt()
        val body = String(response.body())
        body shouldBe expected.path("body_sample").path("head").asText()
        response.body().size shouldBe expected.path("body_sample").path("length").asInt()
        response.headers().firstValue("content-length").get() shouldBe expected.path("headers").path("content-length").asText()
        sameMediaType(response.headers().firstValue("content-type").get(), expected.path("headers").path("content-type").asText())
        response.headers().firstValue("cache-control").get() shouldBe expected.path("headers").path("cache-control").asText()
        response.headers().firstValue("x-frame-options").get() shouldBe expected.path("headers").path("x-frame-options").asText()
        response.headers().firstValue("x-content-type-options").get() shouldBe
            expected.path("headers").path("x-content-type-options").asText()
    }

    @Test
    fun `body is exactly the 73 byte green page`() {
        val response = get()

        response.body().size shouldBe 73
        String(response.body()) shouldBe """<!DOCTYPE html><html><body style="background-color: green"></body></html>"""
    }

    @Test
    fun `creates no session`() {
        val response = get()

        response.headers().allValues("set-cookie").shouldBeEmpty()
    }

    @Test
    fun `is not blocked for legacy browsers`() {
        val response = get("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)", "Accept", "*/*")

        response.statusCode() shouldBe 200
    }

    private fun sameMediaType(
        actual: String,
        expected: String,
    ) {
        // Tomcat serialises "text/html;charset=utf-8" without the space Rack writes; the media type is identical.
        val a = MediaType.parseMediaType(actual)
        val e = MediaType.parseMediaType(expected)
        a.type shouldBe e.type
        a.subtype shouldBe e.subtype
        a.charset shouldBe e.charset
    }
}
