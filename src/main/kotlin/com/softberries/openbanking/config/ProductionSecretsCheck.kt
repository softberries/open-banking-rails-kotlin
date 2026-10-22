package com.softberries.openbanking.config

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** Rails refuses to boot in production without `SECRET_KEY_BASE`; so do we. The entrypoint generates one on first boot. */
@Component
@Profile("production")
class ProductionSecretsCheck(
    private val properties: AppProperties,
) : InitializingBean {
    override fun afterPropertiesSet() {
        check(properties.secretKeyBase.isNotBlank()) {
            "SECRET_KEY_BASE must be set in production (bin/docker-entrypoint generates one into \$SECRETS_DIR)"
        }
    }
}
