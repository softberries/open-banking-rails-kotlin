package com.softberries.openbanking.health.web

import com.softberries.openbanking.web.SkipBrowserGate
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Port of `Rails::HealthController#show` (config/routes.rb:21): liveness only — it never touches the database (U-522),
 * so it answers 200 as soon as the application has booted.
 */
@RestController
@SkipBrowserGate
class UpController {
    @GetMapping(PATH)
    fun show(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_OK
        response.contentType = CONTENT_TYPE
        response.setHeader("Cache-Control", "max-age=0, private, must-revalidate")
        response.setContentLength(BODY.size)
        response.outputStream.write(BODY)
    }

    companion object {
        const val PATH = "/up"
        const val CONTENT_TYPE = "text/html; charset=utf-8"
        val BODY: ByteArray = """<!DOCTYPE html><html><body style="background-color: green"></body></html>""".toByteArray()
    }
}
