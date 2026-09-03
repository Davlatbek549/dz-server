package com.example.dz.server.auth.controller

import com.example.dz.server.auth.dto.AuthResponse
import com.example.dz.server.auth.dto.ForgotPasswordRequest
import com.example.dz.server.auth.dto.LoginRequest
import com.example.dz.server.auth.dto.GoogleSignInRequest
import com.example.dz.server.auth.dto.LogoutRequest
import com.example.dz.server.auth.dto.RefreshRequest
import com.example.dz.server.auth.dto.ResendVerificationRequest
import com.example.dz.server.auth.dto.ResetPasswordRequest
import com.example.dz.server.auth.dto.SignUpRequest
import com.example.dz.server.auth.dto.UserResponse
import com.example.dz.server.auth.dto.VerifyEmailRequest
import com.example.dz.server.auth.entity.VerificationPurpose
import com.example.dz.server.auth.service.AuthService
import com.example.dz.server.auth.service.OAuthService
import com.example.dz.server.auth.verification.VerificationService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Paths and payloads match the app's existing `KtorAuthApi`, so pointing
 * `ApiConfig.baseUrl` at this server is the only client-side change needed.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val oAuthService: OAuthService,
    private val verificationService: VerificationService,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpRequest): AuthResponse =
        authService.signUp(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        authService.login(request)

    /** Trades a Google ID token for a DZ session, creating or linking the account as needed. */
    @PostMapping("/oauth/google")
    fun signInWithGoogle(@Valid @RequestBody request: GoogleSignInRequest): AuthResponse =
        oAuthService.signInWithGoogle(request.idToken)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): AuthResponse =
        authService.refresh(request)

    /** Body is optional; without one, every session for the caller is revoked. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @AuthenticationPrincipal userId: UUID,
        @RequestBody(required = false) request: LogoutRequest?,
    ) {
        authService.logout(userId, request?.refreshToken)
    }

    /**
     * Spends a code and marks the address verified.
     *
     * 204 rather than a session: the caller already holds one from sign-up, and
     * a reset is not a sign-in.
     */
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest) {
        verificationService.verifyEmail(request.email.trim(), request.code.trim())
    }

    /**
     * Sends another code. Always 204, even for an address with no account:
     * a different answer here would be a way to discover who is registered.
     */
    @PostMapping("/verify/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resendVerification(@Valid @RequestBody request: ResendVerificationRequest) {
        verificationService.resend(request.email.trim(), VerificationPurpose.VerifyEmail)
    }

    /**
     * Starts a password reset. Always 204, even for an address with no account,
     * for the same reason [resendVerification] is: a different answer here would
     * be a way to discover who is registered.
     */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest) {
        authService.forgotPassword(request.email)
    }

    /**
     * Spends the reset code and sets the new password.
     *
     * 204 rather than a session, matching [verifyEmail]: a reset is not a sign-in.
     * The reader signs in with the password they just chose, which also proves it
     * is the one they meant to set.
     */
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest) {
        authService.resetPassword(request)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: UUID): UserResponse =
        authService.currentUser(userId)
}
