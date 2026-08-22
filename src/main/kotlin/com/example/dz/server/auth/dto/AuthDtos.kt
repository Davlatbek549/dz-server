package com.example.dz.server.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Field names mirror the client's `AuthDtos.kt`. The app deserializes with
 * `ignoreUnknownKeys = true`, so [AuthResponse]'s extra `refreshToken` and
 * `expiresIn` are ignored by the current build and available as soon as it
 * starts reading them — no coordinated release needed.
 */

data class SignUpRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email is not valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = PASSWORD_MIN_LENGTH, message = "Password must be at least $PASSWORD_MIN_LENGTH characters")
    val password: String,
)

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String,
)

data class RefreshRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String,
)

/** Logout without a body revokes every session for the caller. */
data class LogoutRequest(
    val refreshToken: String? = null,
)

/** The ID token the app received from Google, to be proven before anything is believed. */
data class GoogleSignInRequest(
    @field:NotBlank(message = "idToken is required")
    val idToken: String,
)

data class AuthResponse(
    val token: String,
    val refreshToken: String,
    /** Access token lifetime in seconds, so the client can refresh before expiry. */
    val expiresIn: Long,
    val user: UserResponse,
)

data class UserResponse(
    val id: String,
    val name: String,
    val email: String?,
    val avatarUrl: String?,
)

const val PASSWORD_MIN_LENGTH = 8
