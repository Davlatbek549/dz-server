package com.example.dz.server.auth.service

import com.example.dz.server.auth.dto.AuthResponse
import com.example.dz.server.auth.dto.LoginRequest
import com.example.dz.server.auth.dto.RefreshRequest
import com.example.dz.server.auth.dto.SignUpRequest
import com.example.dz.server.auth.dto.UserResponse
import com.example.dz.server.auth.exception.AuthException
import com.example.dz.server.auth.jwt.JwtService
import com.example.dz.server.auth.mapper.toUserResponse
import com.example.dz.server.users.entity.User
import com.example.dz.server.users.repository.UserRepository
import java.util.UUID
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokens: RefreshTokenService,
) {

    fun signUp(request: SignUpRequest): AuthResponse {
        val email = request.email.trim()
        if (users.existsByEmailIgnoringCase(email)) throw AuthException.emailAlreadyInUse()

        val user = users.save(
            User(
                email = email,
                passwordHash = encodePassword(request.password),
                name = request.name.trim(),
            )
        )
        return issueSession(user)
    }

    fun login(request: LoginRequest): AuthResponse {
        // The same error for an unknown address and a wrong password, so the
        // response cannot be used to discover which emails have accounts.
        val user = users.findByEmailIgnoringCase(request.email.trim())
            .orElseThrow { AuthException.invalidCredentials() }

        // A provider-only account has no hash to compare. Same error as a wrong password, so
        // the response cannot be used to discover which accounts sign in with Google.
        val hash = user.passwordHash ?: throw AuthException.invalidCredentials()
        if (!passwordEncoder.matches(request.password, hash)) {
            throw AuthException.invalidCredentials()
        }
        if (!user.enabled) throw AuthException.userDisabled()

        return issueSession(user)
    }

    fun refresh(request: RefreshRequest): AuthResponse {
        val (user, newRefreshToken) = refreshTokens.rotate(request.refreshToken)
        return AuthResponse(
            token = jwtService.issueAccessToken(user),
            refreshToken = newRefreshToken,
            expiresIn = jwtService.accessTokenTtlSeconds(),
            user = user.toUserResponse(),
        )
    }

    fun logout(userId: UUID, refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) {
            refreshTokens.revokeAllForUser(userId)
        } else {
            refreshTokens.revoke(refreshToken)
        }
    }

    @Transactional(readOnly = true)
    fun currentUser(userId: UUID): UserResponse =
        users.findById(userId)
            .map { it.toUserResponse() }
            .orElseThrow { AuthException.invalidCredentials() }

    /** Spring Security declares `encode` as nullable; it never is for a real password. */
    private fun encodePassword(rawPassword: String): String =
        checkNotNull(passwordEncoder.encode(rawPassword)) { "Password encoder returned no hash" }

    private fun issueSession(user: User) = AuthResponse(
        token = jwtService.issueAccessToken(user),
        refreshToken = refreshTokens.issue(user),
        expiresIn = jwtService.accessTokenTtlSeconds(),
        user = user.toUserResponse(),
    )
}
