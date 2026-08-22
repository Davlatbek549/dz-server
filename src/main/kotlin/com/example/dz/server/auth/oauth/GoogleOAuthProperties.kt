package com.example.dz.server.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * [clientId] is the **Web** OAuth client id from the Google console, not the Android one. The
 * Android app is issued tokens whose `aud` is the web client, and this is what they are checked
 * against — see [GoogleTokenVerifier].
 *
 * It is not a secret: it ships inside every copy of the app. It still belongs in configuration
 * rather than in code, because it differs between the real project and anyone else's.
 */
@ConfigurationProperties(prefix = "dz.oauth.google")
data class GoogleOAuthProperties(
    val clientId: String = "",
) {
    /** Google sign-in is simply off when no client id is configured. */
    val isEnabled: Boolean get() = clientId.isNotBlank()
}
