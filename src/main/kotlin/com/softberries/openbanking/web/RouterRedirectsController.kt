package com.softberries.openbanking.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Router-level `redirect(...)` routes of `config/routes.rb` (`:158` root, `:48` admin root). They are not controllers in Rails,
 * so neither the browser gate, the first-run check nor authentication applies; they answer 301 with an absolute Location
 * built from the request's scheme, host and non-default port, and an empty `text/html` body.
 */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@SkipBrowserGate
class RouterRedirectsController {
    @GetMapping(ROOT)
    fun root(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = redirect(request, response, ADMIN)

    @GetMapping(ADMIN)
    fun admin(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = redirect(request, response, ADMIN_ANALYTICS)

    private fun redirect(
        request: HttpServletRequest,
        response: HttpServletResponse,
        path: String,
    ) {
        response.status = HttpServletResponse.SC_MOVED_PERMANENTLY
        response.setHeader(HttpHeaders.LOCATION, absolute(request, path))
        response.contentType = CONTENT_TYPE
        response.setContentLength(0)
    }

    companion object {
        const val ROOT = "/"
        const val ADMIN = "/admin"
        const val ADMIN_ANALYTICS = "/admin/analytics"
        const val CONTENT_TYPE = "text/html; charset=UTF-8"

        /** Paths answered by this controller, public for everybody (Spring Security permits them). */
        val PATHS = arrayOf(ROOT, ADMIN)

        fun absolute(
            request: HttpServletRequest,
            path: String,
        ): String = "${WebPaths.baseUrl(request)}${request.contextPath}$path"
    }
}
