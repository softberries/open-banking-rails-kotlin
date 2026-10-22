package com.softberries.openbanking.support

import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.SystemEnvironmentPropertySource

/** Supplies values as real environment variables would arrive, so relaxed binding (`APP_TIME_ZONE` → `app.time-zone`) applies. */
fun ApplicationContextRunner.withEnv(vararg pairs: Pair<String, String>): ApplicationContextRunner =
    withInitializer { context ->
        context.environment.propertySources.addFirst(
            SystemEnvironmentPropertySource("test-systemEnvironment", mapOf<String, Any>(*pairs)),
        )
    }
