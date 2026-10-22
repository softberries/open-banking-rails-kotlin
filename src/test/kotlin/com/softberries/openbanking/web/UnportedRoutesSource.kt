package com.softberries.openbanking.web

import org.junit.jupiter.params.provider.CsvSource

/** The 32 Ruby routes that are not ported (D-006, X-16), with sample values for their path parameters. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@CsvSource(
    // /admin/debug* (development only in Ruby)
    "GET, /admin/debug",
    "GET, /admin/debug/runtime_error",
    "GET, /admin/debug/zero_division",
    "GET, /admin/debug/argument_error",
    "GET, /admin/debug/nested_error",
    "POST, /admin/debug/enqueue_failing_job",
    // Turbo Native
    "GET, /recede_historical_location",
    "GET, /resume_historical_location",
    "GET, /refresh_historical_location",
    // ActionMailbox ingresses
    "POST, /rails/action_mailbox/postmark/inbound_emails",
    "POST, /rails/action_mailbox/relay/inbound_emails",
    "POST, /rails/action_mailbox/sendgrid/inbound_emails",
    "GET, /rails/action_mailbox/mandrill/inbound_emails",
    "POST, /rails/action_mailbox/mandrill/inbound_emails",
    "POST, /rails/action_mailbox/mailgun/inbound_emails/mime",
    // Rails Conductor
    "GET, /rails/conductor/action_mailbox/inbound_emails",
    "POST, /rails/conductor/action_mailbox/inbound_emails",
    "GET, /rails/conductor/action_mailbox/inbound_emails/new",
    "GET, /rails/conductor/action_mailbox/inbound_emails/1",
    "GET, /rails/conductor/action_mailbox/inbound_emails/sources/new",
    "POST, /rails/conductor/action_mailbox/inbound_emails/sources",
    "POST, /rails/conductor/action_mailbox/1/reroute",
    "POST, /rails/conductor/action_mailbox/1/incinerate",
    // ActiveStorage
    "GET, /rails/active_storage/blobs/redirect/signed-id/file.png",
    "GET, /rails/active_storage/blobs/proxy/signed-id/file.png",
    "GET, /rails/active_storage/blobs/signed-id/file.png",
    "GET, /rails/active_storage/representations/redirect/signed-id/variation/file.png",
    "GET, /rails/active_storage/representations/proxy/signed-id/variation/file.png",
    "GET, /rails/active_storage/representations/signed-id/variation/file.png",
    "GET, /rails/active_storage/disk/encoded-key/file.png",
    "PUT, /rails/active_storage/disk/encoded-token",
    "POST, /rails/active_storage/direct_uploads",
)
annotation class UnportedRoutesSource
