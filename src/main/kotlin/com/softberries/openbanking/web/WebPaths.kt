package com.softberries.openbanking.web

import jakarta.servlet.http.HttpServletRequest

/** Paths that are not Rails-rendered HTML routes: the liveness probe, the JSON API (Grape) and MCP. */
object WebPaths {
    const val UP = "/up"
    const val API_PREFIX = "/api/"
    const val MCP = "/mcp"

    fun path(request: HttpServletRequest): String = request.requestURI.removePrefix(request.contextPath).ifEmpty { "/" }

    fun isUp(request: HttpServletRequest): Boolean = path(request) == UP

    fun isNonHtml(request: HttpServletRequest): Boolean = isNonHtml(path(request))

    fun isNonHtml(path: String): Boolean = path == UP || path == MCP || path == "/api" || path.startsWith(API_PREFIX)

    /** Rails `request.base_url`: scheme, host and a non-default port. */
    fun baseUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val port = request.serverPort
        val standard = (scheme == "http" && port == HTTP_PORT) || (scheme == "https" && port == HTTPS_PORT)
        return "$scheme://${request.serverName}${if (standard || port <= 0) "" else ":$port"}"
    }

    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
}
