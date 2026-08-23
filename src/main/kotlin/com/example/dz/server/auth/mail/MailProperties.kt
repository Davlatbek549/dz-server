package com.example.dz.server.auth.mail

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @param from the address mail is sent as. It has to sit under the domain
 *   verified with the provider — anything else fails SPF and DKIM, which is
 *   the difference between arriving and being filed as spam.
 * @param apiKey the provider's key. A real secret, unlike the Google client
 *   ids: it grants sending rights on the domain. Blank disables real sending.
 */
@ConfigurationProperties(prefix = "dz.mail")
data class MailProperties(
    val from: String = "dz <noreply@mail.dzreader.com>",
    val apiKey: String = "",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}
