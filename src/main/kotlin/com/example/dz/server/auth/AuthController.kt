package com.example.dz.server.auth

import com.example.dz.server.auth.dto.AuthResponse
import com.example.dz.server.auth.dto.LoginRequest
import com.example.dz.server.auth.dto.LogoutRequest
import com.example.dz.server.auth.dto.RefreshRequest
import com.example.dz.server.auth.dto.SignUpRequest
import com.example.dz.server.auth.dto.UserResponse
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
class AuthController(private val authService: AuthService) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpRequest): AuthResponse =
        authService.signUp(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        authService.login(request)

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

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: UUID): UserResponse =
        authService.currentUser(userId)
}
