package com.softberries.openbanking.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class CoreConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun appClock(properties: AppProperties): AppClock = AppClock(properties.zone)
}
