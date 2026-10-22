package com.softberries.openbanking

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import kotlin.system.exitProcess

// UserDetailsServiceAutoConfiguration would log a generated password; the session-auth unit provides its own UserDetailsService.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
class OpenBankingApplication

/** Argument that runs the Flyway migrations and exits (the Kotlin counterpart of `bin/rails db:prepare` in `bin/docker-migrate`). */
const val MIGRATE_COMMAND = "db:migrate"

fun main(args: Array<String>) {
    if (args.firstOrNull() == MIGRATE_COMMAND) {
        val context =
            SpringApplicationBuilder(OpenBankingApplication::class.java)
                .web(WebApplicationType.NONE)
                .run(*args.drop(1).toTypedArray())
        exitProcess(
            org.springframework.boot.SpringApplication
                .exit(context),
        )
    }
    SpringApplicationBuilder(OpenBankingApplication::class.java).run(*args)
}
