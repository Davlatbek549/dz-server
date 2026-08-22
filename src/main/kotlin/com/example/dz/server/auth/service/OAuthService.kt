package com.example.dz.server.auth.service

import com.example.dz.server.auth.dto.AuthResponse
import com.example.dz.server.auth.entity.AuthProvider
import com.example.dz.server.auth.entity.LinkedAccount
import com.example.dz.server.auth.exception.AuthException
import com.example.dz.server.auth.jwt.JwtService
import com.example.dz.server.auth.mapper.toUserResponse
import com.example.dz.server.auth.oauth.GoogleIdentity
import com.example.dz.server.auth.oauth.GoogleOAuthProperties
import com.example.dz.server.auth.oauth.GoogleTokenVerifier
import com.example.dz.server.auth.repository.LinkedAccountRepository
import com.example.dz.server.users.entity.User
import com.example.dz.server.users.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Turns a proven Google identity into a DZ session.
 *
 * Three cases, in order:
 *
 * 1. **The provider identity is already linked** — sign that user in. The only lookup that uses
 *    Google's `sub`, which never changes.
 * 2. **No link, but a verified address matches an existing account** — link and sign in, so
 *    someone who signed up with a password and later taps Google keeps one account.
 * 3. **Neither** — create an account with no password. It can only be reached through Google
 *    until its owner sets one.
 */
@Service
@Transactional
class OAuthService(
    private val users: UserRepository,
    private val linkedAccounts: LinkedAccountRepository,
    private val verifier: GoogleTokenVerifier,
    private val properties: GoogleOAuthProperties,
    private val jwtService: JwtService,
    private val refreshTokens: RefreshTokenService,
) {

    fun signInWithGoogle(rawIdToken: String): AuthResponse {
        if (!properties.isEnabled) throw AuthException.providerNotConfigured()

        val identity = verifier.verify(rawIdToken)
        val user = resolveUser(identity)

        if (!user.enabled) throw AuthException.userDisabled()

        return AuthResponse(
            token = jwtService.issueAccessToken(user),
            refreshToken = refreshTokens.issue(user),
            expiresIn = jwtService.accessTokenTtlSeconds(),
            user = user.toUserResponse(),
        )
    }

    private fun resolveUser(identity: GoogleIdentity): User {
        val alreadyLinked = linkedAccounts
            .findByProviderAndSubject(AuthProvider.Google, identity.subject)
            .orElse(null)
        if (alreadyLinked != null) return alreadyLinked.user

        val email = identity.email?.trim()

        // Linking on an address Google has not verified would let anyone who can set an
        // arbitrary email on their own Google account walk into someone else's DZ account.
        // Unverified means we do not match — we make a separate account instead.
        val existing = if (identity.emailVerified && !email.isNullOrBlank()) {
            users.findByEmailIgnoringCase(email).orElse(null)
        } else {
            null
        }

        val user = existing ?: users.save(
            User(
                email = email ?: throw AuthException.invalidCredentials(),
                // No password: this account exists only through Google until its owner sets one.
                passwordHash = null,
                name = identity.name?.takeIf { it.isNotBlank() } ?: fallbackName(email),
                avatarUrl = identity.pictureUrl,
            ).apply {
                // Google verified the address, so DZ does not need to ask again.
                emailVerified = identity.emailVerified
            }
        )

        linkedAccounts.save(
            LinkedAccount(
                user = user,
                provider = AuthProvider.Google,
                subject = identity.subject,
                email = email,
            )
        )
        return user
    }

    /** Google can withhold the profile name; the local part is a better placeholder than blank. */
    private fun fallbackName(email: String?): String =
        email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: "Reader"
}
