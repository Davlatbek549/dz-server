package com.example.dz.server.common.exception

import com.example.dz.server.auth.exception.AuthErrorCode
import com.example.dz.server.auth.exception.AuthException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Error body returned by every failing endpoint.
 *
 * [code] carries an [AuthErrorCode] name so the app can map it straight onto
 * `AppError.AuthReason`; [fieldErrors] is for showing messages next to inputs.
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
            fieldErrors.containsKey("email") -> AuthErrorCode.InvalidEmail
            fieldErrors.containsKey("password") -> AuthErrorCode.WeakPassword
            else -> AuthErrorCode.Unknown
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ApiError(
                    code = code.name,
                    message = fieldErrors.values.firstOrNull() ?: "Request is not valid",
                    fieldErrors = fieldErrors,
                )
            )
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
}
