package com.softberries.openbanking.web

import com.softberries.openbanking.web.WebClient.Companion.title
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class HtmlErrorPagesTest {
    private val pages = HtmlErrorPages()

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "400 | The server cannot process the request due to a client error (400 Bad Request)",
            "404 | The page you were looking for doesn't exist (404 Not found)",
            "406 | Your browser is not supported (406 Not Acceptable)",
            "422 | The change you wanted was rejected (422 Unprocessable Entity)",
            "500 | We're sorry, but something went wrong (500 Internal Server Error)",
        ],
    )
    fun `serves the Ruby static pages`(
        status: Int,
        expectedTitle: String,
    ) {
        title(String(pages.page(status)!!)) shouldBe expectedTitle
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "application/json | true",
            "application/vnd.api+json | true",
            "text/html | false",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8 | false",
            "application/json, */* | false",
            "*/* | false",
            "text/html;q=0.5, application/json | true",
            "not a media type | false",
        ],
    )
    fun `JSON only when the request explicitly asks for it`(
        accept: String,
        json: Boolean,
    ) {
        HtmlErrorPages.wantsJson(MockHttpServletRequest().apply { addHeader("Accept", accept) }) shouldBe json
    }

    @Test
    fun `no Accept header means HTML`() {
        HtmlErrorPages.wantsJson(MockHttpServletRequest()) shouldBe false
    }

    @Test
    fun `JSON body uses the Rack reason phrases`() {
        String(HtmlErrorPages.json(404)) shouldBe """{"status":404,"error":"Not Found"}"""
        String(HtmlErrorPages.json(422)) shouldBe """{"status":422,"error":"Unprocessable Content"}"""
        String(HtmlErrorPages.json(500)) shouldBe """{"status":500,"error":"Internal Server Error"}"""
    }

    @Test
    fun `statuses without a page get an empty body`() {
        val response = MockHttpServletResponse()

        pages.render(MockHttpServletRequest(), response, 403)

        response.status shouldBe 403
        response.contentAsByteArray.size shouldBe 0
    }
}
