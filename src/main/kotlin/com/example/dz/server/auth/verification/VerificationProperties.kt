package com.example.dz.server.auth.verification

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @param codeLength digits in a code. Six is what the app's entry field draws.
 * @param ttl how long a code stands. Long enough for mail to arrive and be
 *   read, short enough that an old message in an inbox is not a way in.
 * @param maxAttempts wrong guesses before the code is retired. Six digits is a
 *   million possibilities, which is only out of reach while guesses are capped.
 * @param hashStrength BCrypt cost for the stored code. Lower than a password's
 *   on purpose — see [VerificationCodeHasher] for why that is safe here.
 * @param resendCooldown minimum gap between sends, so the endpoint cannot be
 *   used to flood somebody's inbox or burn the sending quota.
 */
@ConfigurationProperties(prefix = "dz.verification")
data class VerificationProperties(
    val codeLength: Int = 6,
    val ttl: Duration = Duration.ofMinutes(15),
    val maxAttempts: Int = 5,
    val resendCooldown: Duration = Duration.ofSeconds(60),
    val hashStrength: Int = 8,
)
