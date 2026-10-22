package com.softberries.openbanking.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * Keeps the Ruby ENV contract:
 * - `RAILS_ENV` selects the Spring profile when no profile is configured explicitly (`SPRING_PROFILES_ACTIVE` wins);
 *   Rails defaults to `development`.
 * - `DATABASE_URL` (Rails URL form) overrides the datasource, like Rails merges it over database.yml.
 *
 * Runs before config-data processing so the profile drives which application-<profile>.yml files load.
 */
class RailsEnvironmentPostProcessor :
    EnvironmentPostProcessor,
    Ordered {
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + ORDER_OFFSET

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val defaults = mutableMapOf<String, Any>()
        if (environment.getProperty(PROFILES_PROPERTY).isNullOrBlank() && environment.activeProfiles.isEmpty()) {
            defaults[PROFILES_PROPERTY] = environment.getProperty("RAILS_ENV")?.takeIf { it.isNotBlank() } ?: DEFAULT_RAILS_ENV
        }
        if (defaults.isNotEmpty()) {
            environment.propertySources.addLast(MapPropertySource(RAILS_ENV_SOURCE, defaults))
        }

        val databaseUrl = environment.getProperty("DATABASE_URL")?.takeIf { it.isNotBlank() } ?: return
        val jdbc = DatabaseUrl.parse(databaseUrl)
        val overrides =
            buildMap<String, Any> {
                put("spring.datasource.url", jdbc.url)
                jdbc.username?.let { put("spring.datasource.username", it) }
                jdbc.password?.let { put("spring.datasource.password", it) }
            }
        environment.propertySources.addFirst(MapPropertySource(DATABASE_URL_SOURCE, overrides))
    }

    companion object {
        /** Before ConfigDataEnvironmentPostProcessor (HIGHEST_PRECEDENCE + 10), which loads application-<profile>.yml. */
        private const val ORDER_OFFSET = 5
        const val PROFILES_PROPERTY = "spring.profiles.active"
        const val DEFAULT_RAILS_ENV = "development"
        const val RAILS_ENV_SOURCE = "railsEnv"
        const val DATABASE_URL_SOURCE = "databaseUrl"
    }
}
