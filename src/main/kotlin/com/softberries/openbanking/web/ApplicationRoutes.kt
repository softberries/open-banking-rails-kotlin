package com.softberries.openbanking.web

import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.util.ServletRequestPathUtils

/**
 * Answers "would the Rails router dispatch this request to a controller?" before the DispatcherServlet runs.
 *
 * Rails runs CSRF, the browser gate and authentication inside controllers, so a request without a route (for example the
 * unported framework routes, X-16) is a plain 404 for everybody. Spring Security runs before routing; its rules use
 * [routed] / [unrouted] to keep that order. Method mismatches count as unrouted (Rails: routing error).
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ApplicationRoutes(
    @Qualifier("requestMappingHandlerMapping") private val mapping: RequestMappingHandlerMapping,
) {
    /** The controller method that will handle [request], or null when the request has no route. Cached per request. */
    fun handlerFor(request: HttpServletRequest): HandlerMethod? {
        val cached = request.getAttribute(CACHE_ATTRIBUTE)
        if (cached != null) return cached as? HandlerMethod
        val handler = lookup(request)
        request.setAttribute(CACHE_ATTRIBUTE, handler ?: NO_HANDLER)
        return handler
    }

    fun hasRoute(request: HttpServletRequest): Boolean = handlerFor(request) != null

    val routed: RequestMatcher = RequestMatcher { hasRoute(it) }
    val unrouted: RequestMatcher = RequestMatcher { !hasRoute(it) }

    private fun lookup(request: HttpServletRequest): HandlerMethod? {
        // getHandler stores lookup results as request attributes; keep them out of the real request.
        val probe = IsolatedAttributesRequest(request)
        ServletRequestPathUtils.parseAndCache(probe)
        val handler =
            try {
                mapping.getHandler(probe)?.handler as? HandlerMethod
            } catch (
                @Suppress("SwallowedException") e: ServletException,
            ) {
                // 405/406/415 lookups: the path exists but not for this request; Rails has no route for it either.
                null
            }
        // `/error` is Spring's error dispatch target, not an application route.
        return handler?.takeUnless { ErrorController::class.java.isAssignableFrom(it.beanType) }
    }

    private class IsolatedAttributesRequest(
        request: HttpServletRequest,
    ) : HttpServletRequestWrapper(request) {
        private val local = mutableMapOf<String, Any?>()

        override fun getAttribute(name: String): Any? = if (local.containsKey(name)) local[name] else super.getAttribute(name)

        override fun setAttribute(
            name: String,
            o: Any?,
        ) {
            local[name] = o
        }

        override fun removeAttribute(name: String) {
            local[name] = null
        }
    }

    companion object {
        private val CACHE_ATTRIBUTE = ApplicationRoutes::class.java.name + ".handler"
        private val NO_HANDLER = Any()
    }
}
