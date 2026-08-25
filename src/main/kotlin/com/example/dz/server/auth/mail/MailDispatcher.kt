package com.example.dz.server.auth.mail

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Hands a message to the provider off the request thread.
 *
 * The provider is a network round trip to someone else's service, and sign-up
 * has no reason to wait for it: the code row is already committed, so the reader
 * can be on the code screen while the mail is still in flight.
 *
 * A separate bean rather than `@Async` on [VerificationService], because Spring
 * routes `@Async` through a proxy and a call from inside the same class does not
 * cross it — the annotation would silently do nothing.
 */
@Component
class MailDispatcher(private val mailer: Mailer) {

    /**
     * Failures are logged, not raised: nothing is waiting for this, and the
     * reader can ask for another code. Raising here would only kill a pool thread.
     */
    @Async(MAIL_EXECUTOR)
    fun dispatch(to: String, subject: String, html: String, text: String) {
        try {
            mailer.send(to, subject, html, text)
        } catch (error: Exception) {
            log.error("Could not send mail to {}", to, error)
        }
    }

    companion object {
        const val MAIL_EXECUTOR = "mailExecutor"
        private val log = LoggerFactory.getLogger(MailDispatcher::class.java)
    }
}
