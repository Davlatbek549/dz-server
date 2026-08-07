package com.example.dz.server.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint

/**
 * Placeholder security setup for M0: the API is stateless and only the health
 * probes are public. JWT authentication replaces the `authenticated()` default
 * in M1.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // No browser sessions or forms, so CSRF tokens have nothing to protect.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(*PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated()
            }
            // Without an entry point an anonymous request is rejected with 403.
            // The app retries on 401, so unauthenticated has to say 401.
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .build()

    private companion object {
        val PUBLIC_PATHS = arrayOf(
            "/actuator/health",
            "/actuator/info",
            "/api/v1/ping",
        )
    }
}
