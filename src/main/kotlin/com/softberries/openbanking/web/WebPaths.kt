package com.softberries.openbanking.web

import jakarta.servlet.http.HttpServletRequest

/** Paths that are not Rails-rendered HTML routes: the liveness probe, the JSON API (Grape) and MCP. */
object WebPaths {
    const val UP = "/up"
    const val API_PREFIX = "/api/"
    const val MCP = "/mcp"

    fun path(request: HttpServletRequest): String = request.requestURI.removePrefix(request.contextPath).ifEmpty { "/" }

    fun isUp(request: HttpServletRequest): Boolean = path(request) == UP

    fun isNonHtml(request: HttpServletRequest): Boolean {
        val path = path(request)
        return path == UP || path == MCP || path == "/api" || path.startsWith(API_PREFIX)
    }
}
