package com.softberries.openbanking.web

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/** Plain HTTP against the running server: the real servlet filter chain, no redirects followed, no cookie jar. */
class WebClient(
    private val port: Int,
) {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    fun send(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null,
    ): HttpResponse<String> {
        val body =
            form?.entries?.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (body != null) builder.header("Content-Type", "application/x-www-form-urlencoded")
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    fun get(
        path: String,
        headers: Map<String, String> = emptyMap(),
    ) = send("GET", path, headers)

    /** A session cookie and a CSRF token for it, as a rendered form would carry them. */
    fun csrfSession(): Pair<String, String> {
        val response = get("/test/html/csrf")
        val cookie =
            response
                .headers()
                .firstValue("set-cookie")
                .orElseThrow()
                .substringBefore(';')
        return cookie to response.body()
    }

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        fun title(html: String): String? = Regex("<title>(.*?)</title>").find(html)?.groupValues?.get(1)
    }
}
