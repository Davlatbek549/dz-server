package com.example.dz.server.common.config

import com.example.dz.server.auth.jwt.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
    ): SecurityFilterChain =
        http
            // No browser sessions or forms, so CSRF tokens have nothing to protect.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(*PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            // Without an entry point an anonymous request is rejected with 403.
            // The app retries on 401, so unauthenticated has to say 401.
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .build()

    /**
     * BCrypt deliberately costs milliseconds per hash, which is irrelevant for
     * one login and ruinous for an attacker working through a stolen table.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    private companion object {
        val PUBLIC_PATHS = arrayOf(
            "/actuator/health",
            "/actuator/info",
            "/api/v1/ping",
            // Endpoints that establish a session; everything else needs one,
            // including /auth/logout and /auth/me.
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/oauth/google",
            // Reached while signed out: during a password reset there is no session,
            // and the app should be able to finish a stale sign-up on a new device.
            "/api/v1/auth/verify",
            "/api/v1/auth/verify/resend",
        )
    }
}
