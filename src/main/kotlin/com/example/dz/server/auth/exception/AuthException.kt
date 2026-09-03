package com.example.dz.server.auth.exception

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
    EmailNotVerified(HttpStatus.FORBIDDEN),
    ProviderNotConfigured(HttpStatus.SERVICE_UNAVAILABLE),
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

        /**
         * The session is real, the address is not proven. Distinct from [invalidCredentials]
         * because the client must send the reader to finish verifying rather than sign in again.
         */
        fun emailNotVerified() =
            AuthException(AuthErrorCode.EmailNotVerified, "Confirm your email address to continue")

        /**
         * Deliberately vague. Naming which limit was hit — guesses on a code, or how
         * recently one was sent — would tell an attacker which lever they are pulling.
         */
        fun tooManyAttempts() =
            AuthException(AuthErrorCode.TooManyAttempts, "Too many attempts, please try again later")

        /** No client id configured, so no token could ever be checked against one. */
        fun providerNotConfigured() =
            AuthException(AuthErrorCode.ProviderNotConfigured, "Google sign-in is not available")
    }
}
