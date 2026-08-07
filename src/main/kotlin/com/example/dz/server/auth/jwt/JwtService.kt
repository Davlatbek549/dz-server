package com.example.dz.server.auth.jwt

import com.example.dz.server.users.User
import java.time.Instant
import java.util.UUID
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Service

/** Issues and reads the short-lived access tokens that authenticate API calls. */
@Service
class JwtService(
    private val encoder: JwtEncoder,
    private val decoder: JwtDecoder,
    private val properties: JwtProperties,
) {

    fun issueAccessToken(user: User): String {
        val issuedAt = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.accessTokenTtl))
            .subject(user.requireId().toString())
            .claim(CLAIM_EMAIL, user.email)
            .build()

        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    /** Returns null when the token is malformed, expired or not signed by us. */
    fun readToken(token: String): Jwt? =
        try {
            decoder.decode(token)
        } catch (_: JwtException) {
            null
        }

    fun accessTokenTtlSeconds(): Long = properties.accessTokenTtl.seconds

    companion object {
        const val CLAIM_EMAIL = "email"

        fun Jwt.userId(): UUID? = runCatching { UUID.fromString(subject) }.getOrNull()
    }
}
