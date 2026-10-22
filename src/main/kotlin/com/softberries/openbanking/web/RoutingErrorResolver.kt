package com.softberries.openbanking.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView

/**
 * A known HTML path requested with another verb has no Rails route (`ActionController::RoutingError`), so it answers 404 with
 * the 404 page (30-http-api.md B.1.1), not Spring's 405 with `Allow`. It runs before Spring's `DefaultHandlerExceptionResolver`.
 * The JSON API and `/mcp` keep 405 (Grape `MethodNotAllowed`, `McpController`).
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RoutingErrorResolver :
    HandlerExceptionResolver,
    Ordered {
    override fun resolveException(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any?,
        ex: Exception,
    ): ModelAndView? {
        if (ex !is HttpRequestMethodNotSupportedException || WebPaths.isNonHtml(request)) return null
        response.sendError(HttpServletResponse.SC_NOT_FOUND)
        return ModelAndView()
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}
