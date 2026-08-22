package com.example.dz.server.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Every OAuth client id whose tokens this server will accept.
 *
 * A list rather than a single value because the audience differs per platform: Android's
 * Credential Manager mints tokens against the **Web** client, while GoogleSignIn-iOS mints them
 * against the **iOS** client. Google's own verification guidance is to check `aud` against the
 * set of client ids belonging to the application, which is exactly this.
 *
 * None of them are secrets — they ship inside the apps — but they differ per project, so they
 * stay in configuration rather than in code.
 */
@ConfigurationProperties(prefix = "dz.oauth.google")
data class GoogleOAuthProperties(
    val clientIds: List<String> = emptyList(),
) {
    /** Blank entries are dropped so a trailing comma in the environment cannot widen the set. */
    val acceptedAudiences: Set<String> =
        clientIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** Google sign-in is simply off when no client id is configured. */
    val isEnabled: Boolean get() = acceptedAudiences.isNotEmpty()
}
