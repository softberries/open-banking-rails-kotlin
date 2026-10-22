package com.softberries.openbanking.web

/**
 * Marks a controller that does not inherit Rails' `ApplicationController` (router-level redirects, health check), so the
 * modern-browser gate (C1) does not apply to it.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SkipBrowserGate
