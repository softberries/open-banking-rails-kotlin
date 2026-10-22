package com.softberries.openbanking.web

import com.softberries.openbanking.config.AppProperties
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

/**
 * Transport rules of `config/environments/production.rb`; development and test set neither (Rails defaults).
 * - [forceSsl]: `assume_ssl` + `force_ssl` when `FORCE_SSL` is exactly "true" (`:30-31`).
 * - [allowedHosts]: `config.hosts` from `APP_HOSTS`, falling back to `APP_HOST`; empty disables the check (`:95-103`).
 */
data class WebTransportSettings(
    val forceSsl: Boolean,
    val allowedHosts: List<String>,
) {
    companion object {
        const val PRODUCTION = "production"

        fun from(
            properties: AppProperties,
            environment: Environment,
        ): WebTransportSettings {
            val production = environment.acceptsProfiles(Profiles.of(PRODUCTION))
            return WebTransportSettings(
                forceSsl = production && properties.forceSslEnabled,
                allowedHosts = if (production) properties.allowedHosts else emptyList(),
            )
        }
    }
}
