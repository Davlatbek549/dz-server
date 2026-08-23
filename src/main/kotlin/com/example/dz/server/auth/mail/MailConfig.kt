package com.example.dz.server.auth.mail

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Picks the sender from configuration: a real one where a key is set, and a
 * logging stand-in everywhere else.
 *
 * Chosen here rather than with `@ConditionalOnProperty` on each class so the
 * rule is in one readable place, and so the fallback is a deliberate object
 * rather than the absence of a bean.
 */
@Configuration
class MailConfig {

    @Bean
    fun mailer(properties: MailProperties): Mailer =
        if (properties.isConfigured) ResendMailer(properties) else LoggingMailer()
}
