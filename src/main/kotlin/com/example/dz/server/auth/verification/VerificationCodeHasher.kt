package com.example.dz.server.auth.verification

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/**
 * Hashes verification codes — deliberately at a lower cost than passwords.
 *
 * A password must survive an offline attack indefinitely, so its work factor is
 * set against hardware years from now. A code is six digits, lives fifteen
 * minutes, is spendable once and tolerates five guesses. Against a leaked dump,
 * strength 8 costs roughly twenty milliseconds a guess, so the million
 * possibilities take hours — long after the code it protects has expired.
 *
 * Paying the password work factor here would buy nothing and put it on the
 * sign-up request, which on a small container is time the reader waits.
 *
 * Its own type rather than a second [org.springframework.security.crypto.password.PasswordEncoder]
 * bean, which would make every existing injection of one ambiguous.
 */
@Component
class VerificationCodeHasher(properties: VerificationProperties) {

    private val encoder = BCryptPasswordEncoder(properties.hashStrength)

    /** Spring Security declares `encode` as nullable; it never is for a real code. */
    fun hash(code: String): String =
        checkNotNull(encoder.encode(code)) { "Password encoder returned no hash" }

    fun matches(code: String, hash: String): Boolean = encoder.matches(code, hash)
}
