package com.example.dz.server.auth.mail

import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient

/**
 * Sends through Resend's HTTP API rather than SMTP: hosts commonly block
 * outbound port 25, and a provider handles the reputation work that decides
 * whether a verification code reaches an inbox at all.
 */
class ResendMailer(
    private val properties: MailProperties,
    private val restClient: RestClient = RestClient.create(),
) : Mailer {

    override fun send(to: String, subject: String, html: String, text: String) {
        restClient.post()
            .uri(ENDPOINT)
            .header("Authorization", "Bearer ${properties.apiKey}")
            .body(
                mapOf(
                    "from" to properties.from,
                    "to" to listOf(to),
                    "subject" to subject,
                    "html" to html,
                    // Sent alongside the HTML rather than instead of it: a
                    // message with no plain-text part scores as spam.
                    "text" to text,
                )
            )
            .retrieve()
            .toBodilessEntity()
    }

    private companion object {
        const val ENDPOINT = "https://api.resend.com/emails"
    }
}

/**
 * Stands in wherever no provider key is set — local development, and tests.
 *
 * It logs the message instead of sending it, so the whole flow is exercisable
 * without a network or an account: the code appears in the server output.
 * Deliberately never active in prod, where a missing key fails startup instead.
 */
class LoggingMailer : Mailer {

    override fun send(to: String, subject: String, html: String, text: String) {
        log.info("[mail disabled] would send to {} | {} | {}", to, subject, text)
    }

    private companion object {
        val log = LoggerFactory.getLogger(LoggingMailer::class.java)
    }
}
