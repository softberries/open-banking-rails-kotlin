package com.softberries.openbanking.web

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Replaces Spring Boot's `BasicErrorController` (whitelabel page / JSON with trace) on the servlet error dispatch. Every
 * `sendError` and every exception that escapes a controller or filter ends here and is answered with [HtmlErrorPages]; the
 * exception itself is only logged (U-516). Mapped exceptions: [RecordNotFoundException] → 404, [ParameterMissingException]
 * and Spring's binding errors → 400, CSRF failure → 422, anything else → 500.
 */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ErrorPagesController(
    private val pages: HtmlErrorPages,
) : ErrorController {
    private val log = LoggerFactory.getLogger(javaClass)

    @RequestMapping(PATH)
    fun error(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        // Requested directly (not an error dispatch): Rails has no such route.
        val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int ?: HttpStatus.NOT_FOUND.value()
        val exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as? Throwable
        if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("{} {} failed with {}", request.method, request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI), status, exception)
        }
        if (!response.isCommitted) pages.render(request, response, status)
    }

    companion object {
        const val PATH = "/error"
    }
}
