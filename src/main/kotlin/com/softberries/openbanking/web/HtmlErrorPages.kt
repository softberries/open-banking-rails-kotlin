package com.softberries.openbanking.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

/**
 * The static error pages of the Ruby app (`public/400.html` … `public/500.html`), served the way Rails' production
 * `ActionDispatch::PublicExceptions` does, in every profile (U-516: never an exception page or stack trace).
 *
 * A request that explicitly asks for JSON gets `{"status":<code>,"error":"<reason>"}` instead of the page.
 * Statuses without a page (e.g. 403, or a 406 that is not the browser gate) get an empty body, as Rails'
 * `ShowExceptions#pass_response` does when `public/<status>.html` is missing.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class HtmlErrorPages {
    private val pages: Map<Int, ByteArray> =
        mapOf(
            HttpStatus.BAD_REQUEST.value() to "400.html",
            HttpStatus.NOT_FOUND.value() to "404.html",
            HttpStatus.UNPROCESSABLE_ENTITY.value() to "422.html",
            HttpStatus.INTERNAL_SERVER_ERROR.value() to "500.html",
        ).mapValues { (_, file) -> load(file) }

    /** Rendered only by the browser gate (`allow_browser`); Ruby has no `public/406.html`. */
    val unsupportedBrowserPage: ByteArray = load(UNSUPPORTED_BROWSER_PAGE)

    fun page(status: Int): ByteArray? = pages[status]

    /** Writes the error response for [status], choosing HTML or JSON from the `Accept` header. */
    fun render(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: Int,
    ) {
        if (wantsJson(request)) {
            write(response, status, JSON_UTF8, json(status))
        } else {
            write(response, status, HTML_UTF8, pages[status] ?: ByteArray(0))
        }
    }

    /** The 406 page of the browser gate; Rails renders it as HTML whatever the request asks for (C1). */
    fun renderUnsupportedBrowser(response: HttpServletResponse) {
        write(response, HttpStatus.NOT_ACCEPTABLE.value(), HTML_UTF8, unsupportedBrowserPage)
    }

    private fun load(file: String): ByteArray = ClassPathResource("public/$file").contentAsByteArray

    private fun write(
        response: HttpServletResponse,
        status: Int,
        contentType: String,
        body: ByteArray,
    ) {
        response.resetBuffer()
        response.status = status
        response.contentType = contentType
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_CACHE)
        response.setContentLength(body.size)
        if (body.isNotEmpty()) response.outputStream.write(body)
        response.flushBuffer()
    }

    companion object {
        const val UNSUPPORTED_BROWSER_PAGE = "406-unsupported-browser.html"
        const val HTML_UTF8 = "text/html; charset=utf-8"
        const val JSON_UTF8 = "application/json; charset=utf-8"
        const val NO_CACHE = "no-cache"

        /** Rack 3.2 `Rack::Utils::HTTP_STATUS_CODES` reason phrases where they differ from Spring's. */
        private val RACK_REASONS =
            mapOf(
                HttpStatus.PAYLOAD_TOO_LARGE.value() to "Content Too Large",
                HttpStatus.UNPROCESSABLE_ENTITY.value() to "Unprocessable Content",
            )

        /** Rails `request.formats`: a browser-like Accept (the any-type wildcard next to other types) means HTML. */
        private val BROWSER_LIKE_ACCEPTS = Regex(""",\s*\*/\*|\*/\*\s*,""")

        fun reason(status: Int): String = RACK_REASONS[status] ?: HttpStatus.resolve(status)?.reasonPhrase ?: "Unknown"

        fun json(status: Int): ByteArray = """{"status":$status,"error":"${reason(status)}"}""".toByteArray()

        fun wantsJson(request: HttpServletRequest): Boolean {
            val accept = request.getHeader(HttpHeaders.ACCEPT)
            if (accept.isNullOrBlank() || BROWSER_LIKE_ACCEPTS.containsMatchIn(accept)) return false
            val first =
                try {
                    MediaType.parseMediaTypes(accept).sortedByDescending { it.qualityValue }.firstOrNull()
                } catch (_: InvalidMediaTypeException) {
                    null
                } ?: return false
            return first.type == "application" && (first.subtype == "json" || first.subtype.endsWith("+json"))
        }
    }
}
