package com.example.dz.server.auth.jwt

import com.nimbusds.jose.jwk.source.ImmutableSecret
import javax.crypto.spec.SecretKeySpec
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

/**
 * One symmetric key signs and verifies tokens, which is the right shape while a
 * single service issues and consumes them. Handing verification to other
 * services later would mean moving to an asymmetric key pair so they can verify
 * without being able to sign.
 */
@Configuration
class JwtConfig(private val properties: JwtProperties) {

    private val secretKey = SecretKeySpec(properties.secret.toByteArray(), HMAC_SHA256)

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey))

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build()

    private companion object {
        const val HMAC_SHA256 = "HmacSHA256"
    }
}
