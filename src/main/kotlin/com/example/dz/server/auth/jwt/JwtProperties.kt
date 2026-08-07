package com.example.dz.server.auth.jwt

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from the `dz.jwt.*` block in application.yaml.
 *
 * [secret] signs access tokens with HMAC-SHA256 and must be at least 32 bytes.
 * Anyone holding it can mint tokens for any account, so production supplies it
 * through the environment and never through a committed file.
 */
@ConfigurationProperties(prefix = "dz.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "dz-server",
    /** Short by design: a stolen access token stays useful only until it expires. */
    val accessTokenTtl: Duration = Duration.ofMinutes(15),
    val refreshTokenTtl: Duration = Duration.ofDays(30),
) {
    init {
        require(secret.toByteArray().size >= MIN_SECRET_BYTES) {
            "dz.jwt.secret must be at least $MIN_SECRET_BYTES bytes for HMAC-SHA256"
        }
    }

    private companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
