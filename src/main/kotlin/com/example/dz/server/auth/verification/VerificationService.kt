package com.example.dz.server.auth.verification

import com.example.dz.server.auth.entity.VerificationCode
import com.example.dz.server.auth.entity.VerificationPurpose
import com.example.dz.server.auth.exception.AuthException
import com.example.dz.server.auth.mail.MailDispatcher
import com.example.dz.server.auth.repository.VerificationCodeRepository
import com.example.dz.server.users.entity.User
import com.example.dz.server.users.repository.UserRepository
import java.security.SecureRandom
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Issues and checks the codes that prove someone reads a mailbox.
 *
 * Both flows that need one — confirming an address, resetting a password —
 * share this, because the rules that make a six-digit secret safe are the same
 * either way: short life, capped guesses, one use, and one live code at a time.
 */
@Service
@Transactional
class VerificationService(
    private val codes: VerificationCodeRepository,
    private val users: UserRepository,
    private val hasher: VerificationCodeHasher,
    private val mail: MailDispatcher,
    private val properties: VerificationProperties,
) {
    private val random = SecureRandom()

    /**
     * Mints a code, retires any earlier one, and mails it.
     *
     * Called from sign-up, so a failure to send must not undo the account:
     * see [issueAndSend]'s caller for where the transaction boundary sits.
     */
    fun issueAndSend(user: User, purpose: VerificationPurpose) {
        val userId = user.requireId()
        val now = Instant.now()

        // Refuse to send again immediately, so the endpoint cannot be used to
        // flood an inbox. Checked against the newest code regardless of whether
        // it was consumed, because sending is what is being limited, not use.
        codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
            .filter { it.createdAt.plus(properties.resendCooldown).isAfter(now) }
            .ifPresent { throw AuthException.tooManyAttempts() }

        codes.consumeAllForUser(userId, purpose, now)

        val code = generateCode()
        codes.save(
            VerificationCode(
                user = user,
                codeHash = hasher.hash(code),
                purpose = purpose,
                expiresAt = now.plus(properties.ttl),
            )
        )
        // Handed off rather than awaited: the row is committed either way, so
        // sign-up has no reason to wait on someone else's API. Failures are the
        // dispatcher's to log — see [MailDispatcher].
        mail.dispatch(
            to = user.email,
            subject = subjectFor(purpose),
            html = htmlFor(purpose, user.name, code),
            text = textFor(purpose, code),
        )
    }

    /**
     * Spends [code] on behalf of [email], marking the address verified.
     *
     * A wrong code, an unknown address and an expired code all raise the same
     * refusal: telling them apart would turn this into a way to learn which
     * addresses have accounts.
     */
    fun verifyEmail(email: String, code: String) {
        val user = users.findByEmailIgnoringCase(email)
            .orElseThrow { AuthException.invalidCredentials() }

        consume(user, VerificationPurpose.VerifyEmail, code)
        user.emailVerified = true
    }

    /**
     * Checks and spends a code, or raises. Shared by every purpose, so the
     * attempt cap and single-use rule cannot be implemented differently twice.
     */
    fun consume(user: User, purpose: VerificationPurpose, code: String): VerificationCode {
        val stored = codes
            .findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.requireId(), purpose)
            .orElseThrow { AuthException.invalidCredentials() }

        if (!stored.isUsable()) throw AuthException.invalidCredentials()

        // Counted before the comparison, so a guess costs an attempt whether or
        // not it lands. Incrementing only on failure would leave the counter
        // resettable by interleaving one correct-looking guess.
        stored.attempts += 1
        if (stored.attempts > properties.maxAttempts) {
            stored.consumedAt = Instant.now()
            throw AuthException.tooManyAttempts()
        }

        if (!hasher.matches(code, stored.codeHash)) throw AuthException.invalidCredentials()

        stored.consumedAt = Instant.now()
        return stored
    }

    /**
     * Resends for an address, saying nothing about whether it is registered.
     *
     * An unknown address is answered exactly as a known one is — no mail, no
     * error — because a different response here would enumerate accounts.
     */
    fun resend(email: String, purpose: VerificationPurpose) {
        val user = users.findByEmailIgnoringCase(email).orElse(null) ?: return
        if (purpose == VerificationPurpose.VerifyEmail && user.emailVerified) return

        // The cooldown is swallowed rather than reported. Answering 429 for an address that has
        // just been mailed and 204 for one with no account would tell them apart, which is the
        // enumeration this endpoint exists to avoid. The reader is not left guessing either way:
        // the screen runs its own countdown before it offers the button.
        runCatching { issueAndSend(user, purpose) }
            .onFailure { if (it !is AuthException) throw it }
    }

    /**
     * Uniformly distributed over the whole range, including codes with leading
     * zeros. `nextInt(bound)` rather than a modulo of a larger draw, which
     * would make low codes fractionally likelier.
     */
    private fun generateCode(): String {
        var bound = 1
        repeat(properties.codeLength) { bound *= 10 }
        return random.nextInt(bound).toString().padStart(properties.codeLength, '0')
    }

    private fun subjectFor(purpose: VerificationPurpose) = when (purpose) {
        VerificationPurpose.VerifyEmail -> "Confirm your email"
        VerificationPurpose.ResetPassword -> "Reset your password"
    }

    private fun textFor(purpose: VerificationPurpose, code: String): String {
        val minutes = properties.ttl.toMinutes()
        return when (purpose) {
            VerificationPurpose.VerifyEmail ->
                "Your dz confirmation code is $code. It expires in $minutes minutes."
            VerificationPurpose.ResetPassword ->
                "Your dz password reset code is $code. It expires in $minutes minutes. " +
                    "If you did not ask for this, you can ignore this email."
        }
    }

    private fun htmlFor(purpose: VerificationPurpose, name: String, code: String): String {
        val minutes = properties.ttl.toMinutes()
        val lead = when (purpose) {
            VerificationPurpose.VerifyEmail -> "Confirm your email to finish setting up dz."
            VerificationPurpose.ResetPassword -> "Use this code to set a new password."
        }
        return """
            <div style="font-family:system-ui,sans-serif;color:#201e1d;background:#f5ead8;padding:32px">
              <p>Hi ${name.escapeHtml()},</p>
              <p>$lead</p>
              <p style="font-size:32px;letter-spacing:8px;font-weight:700;color:#c67139">$code</p>
              <p>It expires in $minutes minutes.</p>
            </div>
        """.trimIndent()
    }

    /** The name is user-supplied and goes into markup, so it cannot travel raw. */
    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
