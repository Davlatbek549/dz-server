package com.example.dz.server.common.exception

import com.example.dz.server.auth.exception.AuthErrorCode
import com.example.dz.server.auth.exception.AuthException
import com.example.dz.server.users.exception.UserNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Error body returned by every failing endpoint.
 *
 * On the auth endpoints [code] is always an [AuthErrorCode] name, so the app
 * maps it straight onto `AppError.AuthReason`. Other modules contribute their
 * own codes, so clients should treat an unrecognised one as "unknown" rather
 * than failing. [fieldErrors] is for showing messages next to inputs.
 */
data class ApiError(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String>? = null,
)

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AuthException::class)
    fun handleAuth(exception: AuthException): ResponseEntity<ApiError> =
        ResponseEntity
            .status(exception.code.status)
            .body(ApiError(code = exception.code.name, message = exception.message))

    /**
     * Bean-validation failures are reported with the same codes as the
     * hand-thrown ones, so a rejected email looks identical to the client
     * whether validation or the service caught it.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fieldErrors = exception.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Invalid value")
        }
        val code = when {
            fieldErrors.containsKey("email") -> AuthErrorCode.InvalidEmail.name
            fieldErrors.containsKey("password") -> AuthErrorCode.WeakPassword.name
            else -> CODE_VALIDATION_FAILED
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ApiError(
                    code = code,
                    message = fieldErrors.values.firstOrNull() ?: "Request is not valid",
                    fieldErrors = fieldErrors,
                )
            )
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(exception: UserNotFoundException): ResponseEntity<ApiError> {
        // The id is logged but kept out of the response, which only needs to say
        // the profile is gone.
        log.warn("Authenticated request for a missing user", exception)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiError(code = CODE_USER_NOT_FOUND, message = "Profile not found"))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ApiError> {
        // Logged in full, but never echoed back — stack traces and SQL in a
        // response body are a gift to anyone probing the API.
        log.error("Unhandled exception", exception)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError(code = AuthErrorCode.Unknown.name, message = "Something went wrong"))
    }

    private companion object {
        const val CODE_VALIDATION_FAILED = "ValidationFailed"
        const val CODE_USER_NOT_FOUND = "UserNotFound"
    }
}
