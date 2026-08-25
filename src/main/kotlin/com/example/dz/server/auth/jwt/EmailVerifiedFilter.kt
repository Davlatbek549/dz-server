package com.example.dz.server.auth.jwt

import com.example.dz.server.auth.exception.AuthException
import com.example.dz.server.users.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Refuses authenticated work for an account whose address is still unproven.
 *
 * Sign-up issues a session before the code is spent, so that the app can hold one while the reader
 * reads their mail. Without this, that session is simply an account — background the app, come
 * back, and verification never happens. Gating it in the client alone would stop nobody holding
 * the token.
 *
 * Runs after [JwtAuthenticationFilter], so it sees the principal that filter established. An
 * anonymous request is none of its business: the authorization rules already decide those.
 */
@Component
class EmailVerifiedFilter(
    private val users: UserRepository,
    /**
     * A filter runs outside the DispatcherServlet, so `@RestControllerAdvice` never sees what it
     * throws — the refusal would surface as a bare 500. Handing the exception to the resolver puts
     * it back through [com.example.dz.server.common.exception.ApiExceptionHandler], so the client
     * reads the same `{code, message}` body it gets from every other refusal.
     */
    @Qualifier("handlerExceptionResolver")
    private val exceptionResolver: HandlerExceptionResolver,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
        if (userId != null && !request.isExempt()) {
            // One lookup per gated request. The alternative — a claim in the access token — costs
            // nothing per request but goes stale for up to its lifetime, so a reader who has just
            // verified would keep being refused. Correctness is worth the query at this size.
            val verified = users.findById(userId).map { it.emailVerified }.orElse(false)
            if (!verified) {
                exceptionResolver.resolveException(
                    request, response, null, AuthException.emailNotVerified()
                )
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    /**
     * What an unverified session may still do: finish verifying, keep its session alive, read who
     * it is, and sign out. Locking these would leave the reader unable to escape the gate.
     */
    private fun HttpServletRequest.isExempt(): Boolean =
        EXEMPT_PATHS.any { requestURI == it }

    private companion object {
        val EXEMPT_PATHS = setOf(
            "/api/v1/auth/verify",
            "/api/v1/auth/verify/resend",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/me",
        )
    }
}
