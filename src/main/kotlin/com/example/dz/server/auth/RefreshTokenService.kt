package com.example.dz.server.auth

import com.example.dz.server.auth.jwt.JwtProperties
import com.example.dz.server.users.User
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Refresh tokens are opaque random strings rather than JWTs: their whole point
 * is being revocable, which means every use is a database lookup anyway, and a
 * random string carries no readable claims if it leaks.
 */
@Service
@Transactional
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    private val properties: JwtProperties,
) {
    private val random = SecureRandom()

    fun issue(user: User): String {
        val rawToken = generateRawToken()
        repository.save(
            RefreshToken(
                user = user,
                tokenHash = hash(rawToken),
                expiresAt = Instant.now().plus(properties.refreshTokenTtl),
            )
        )
        return rawToken
    }

    /**
     * Consumes [rawToken] and returns its owner with a freshly issued replacement.
     *
     * Rotating on every use means a stolen refresh token is only good until the
     * real client refreshes next, at which point the stolen copy stops working.
     */
    fun rotate(rawToken: String): Pair<User, String> {
        val stored = repository.findByTokenHash(hash(rawToken))
            .orElseThrow { AuthException.invalidCredentials() }

        if (!stored.isUsable()) throw AuthException.invalidCredentials()

        val user = stored.user
        if (!user.enabled) throw AuthException.userDisabled()

        stored.revokedAt = Instant.now()
        return user to issue(user)
    }

    fun revoke(rawToken: String) {
        repository.findByTokenHash(hash(rawToken)).ifPresent { token ->
            if (token.revokedAt == null) token.revokedAt = Instant.now()
        }
    }

    fun revokeAllForUser(userId: UUID) {
        repository.revokeAllForUser(userId, Instant.now())
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * SHA-256 rather than BCrypt: these are high-entropy random values, so
     * there is nothing to brute-force, and lookup needs a deterministic hash.
     */
    private fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
