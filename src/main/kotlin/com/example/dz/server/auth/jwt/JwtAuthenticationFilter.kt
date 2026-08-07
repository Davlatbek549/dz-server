package com.example.dz.server.auth.jwt

import com.example.dz.server.auth.jwt.JwtService.Companion.userId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Turns a valid `Authorization: Bearer <token>` header into an authenticated
 * security context, so downstream code reads the caller's id from Spring
 * Security rather than trusting anything in the request body.
 *
 * A missing or bad token is not rejected here — the filter simply leaves the
 * context empty and lets the authorization rules decide, which is what keeps
 * the public endpoints reachable.
 */
@Component
class JwtAuthenticationFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.bearerToken()
        if (token != null && SecurityContextHolder.getContext().authentication == null) {
            jwtService.readToken(token)?.userId()?.let { userId ->
                val authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun HttpServletRequest.bearerToken(): String? =
        getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
