package com.example.dz.server.auth.oauth

import com.example.dz.server.auth.exception.AuthException
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component

/**
 * What Google asserted about a person, once the assertion has been proven genuine.
 *
 * [subject] is Google's permanent id for the account. [emailVerified] is Google's word that the
 * address belongs to them; an unverified one is worthless for matching, because anyone can put
 * any address on a Google account they control.
 */
data class GoogleIdentity(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
    val pictureUrl: String?,
)

/**
 * Validates a Google ID token.
 *
 * The token arrives from the app, which means it arrives from wherever the caller likes — a
 * forged one would be a free account takeover if it were merely decoded. Every check below has
 * to pass before a single claim is believed:
 *
 * - the signature, against Google's published keys, fetched and cached by [NimbusJwtDecoder];
 * - `iss`, so a token signed under a different key set is refused;
 * - `aud`, so a token minted for someone else's app cannot be replayed at ours;
 * - `exp`, which [JwtValidators.createDefault] enforces.
 */
@Component
class GoogleTokenVerifier(properties: GoogleOAuthProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val decoder: NimbusJwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefault(),
                    issuedByGoogle(),
                    issuedForThisApp(properties.clientId),
                )
            )
        }

    fun verify(rawIdToken: String): GoogleIdentity {
        val jwt = try {
            decoder.decode(rawIdToken)
        } catch (exception: JwtException) {
            // Logged, never returned: telling a caller why their forgery was refused is free
            // help towards the next attempt.
            log.warn("Rejected a Google ID token", exception)
            throw AuthException.invalidCredentials()
        }

        val subject = jwt.subject
        if (subject.isNullOrBlank()) throw AuthException.invalidCredentials()

        return GoogleIdentity(
            subject = subject,
            email = jwt.getClaimAsString("email"),
            emailVerified = jwt.getClaimAsBoolean("email_verified") ?: false,
            name = jwt.getClaimAsString("name"),
            pictureUrl = jwt.getClaimAsString("picture"),
        )
    }

    /** Google stamps `iss` with either spelling, and both are legitimate for an ID token. */
    private fun issuedByGoogle() = OAuth2TokenValidator<Jwt> { jwt ->
        if (jwt.issuer?.toString() in ACCEPTED_ISSUERS) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_issuer", "ID token was not issued by Google", null)
            )
        }
    }

    /**
     * Without this, any Google ID token from any app in the world would be accepted — they are
     * all signed by the same keys. The audience is what makes one ours.
     */
    private fun issuedForThisApp(clientId: String) = OAuth2TokenValidator<Jwt> { jwt ->
        if (clientId.isNotBlank() && jwt.audience?.contains(clientId) == true) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_audience", "ID token was not issued for this application", null)
            )
        }
    }

    private companion object {
        const val JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs"
        val ACCEPTED_ISSUERS = setOf("https://accounts.google.com", "accounts.google.com")
    }
}
