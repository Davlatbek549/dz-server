package com.example.dz.server.auth

import org.springframework.http.HttpStatus

/**
 * The reasons the client is able to tell apart in its UI. The names match the
 * app's `AppError.AuthReason` entries exactly, so the client maps a response
 * with `AuthReason.valueOf(code)` and falls back to `Unknown`.
 */
enum class AuthErrorCode(val status: HttpStatus) {
    InvalidCredentials(HttpStatus.UNAUTHORIZED),
    EmailAlreadyInUse(HttpStatus.CONFLICT),
    InvalidEmail(HttpStatus.BAD_REQUEST),
    WeakPassword(HttpStatus.BAD_REQUEST),
    UserDisabled(HttpStatus.FORBIDDEN),
    TooManyAttempts(HttpStatus.TOO_MANY_REQUESTS),
    Unknown(HttpStatus.INTERNAL_SERVER_ERROR),
}

class AuthException(
    val code: AuthErrorCode,
    override val message: String,
) : RuntimeException(message) {

    companion object {
        fun invalidCredentials() =
            AuthException(AuthErrorCode.InvalidCredentials, "Email or password is incorrect")

        fun emailAlreadyInUse() =
            AuthException(AuthErrorCode.EmailAlreadyInUse, "That email is already registered")

        fun weakPassword(minLength: Int) =
            AuthException(AuthErrorCode.WeakPassword, "Password must be at least $minLength characters")

        fun userDisabled() =
            AuthException(AuthErrorCode.UserDisabled, "This account has been disabled")
    }
}
