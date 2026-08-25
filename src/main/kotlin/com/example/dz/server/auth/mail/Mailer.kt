package com.example.dz.server.auth.mail

/**
 * Sending one message, kept behind an interface for two reasons: tests must not
 * reach the network, and a deployment with no mail provider configured should
 * still start and still sign people up.
 */
interface Mailer {
    fun send(to: String, subject: String, html: String, text: String)
}
