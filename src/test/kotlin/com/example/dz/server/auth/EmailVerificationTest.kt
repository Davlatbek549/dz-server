package com.example.dz.server.auth

import com.example.dz.server.auth.entity.VerificationPurpose
import com.example.dz.server.auth.mail.Mailer
import com.example.dz.server.auth.verification.VerificationService
import com.example.dz.server.auth.repository.VerificationCodeRepository
import com.example.dz.server.users.repository.UserRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import com.example.dz.server.auth.exception.AuthException
import com.example.dz.server.auth.exception.AuthErrorCode

/**
 * Captures what would have been emailed, so the code is readable without a
 * provider and no test ever reaches the network.
 */
class CapturingMailer : Mailer {
    val sent = mutableListOf<Triple<String, String, String>>()

    override fun send(to: String, subject: String, html: String, text: String) {
        sent += Triple(to, subject, text)
    }

    /** The six digits out of the message body, which is how a reader gets them. */
    fun lastCode(): String = Regex("\\b(\\d{6})\\b").find(sent.last().third)!!.groupValues[1]

    fun clear() = sent.clear()
}

/**
 * The rules that make a six-digit secret safe: it expires, it is guessable only
 * a handful of times, it works once, and asking about it says nothing about who
 * has an account.
 *
 * Needs the local Postgres from docker-compose, like [AuthFlowTest].
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationTest {

    @TestConfiguration
    class Mail {
        @Bean
        @Primary
        fun capturingMailer() = CapturingMailer()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var mailer: CapturingMailer
    @Autowired private lateinit var verification: VerificationService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var codes: VerificationCodeRepository

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@example.com"

    private fun signUp(email: String) = mockMvc.post("/api/v1/auth/signup") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"name":"Ada Lovelace","email":"$email","password":"correct-horse-battery"}"""
    }.andReturn()

    private fun verify(email: String, code: String) = mockMvc.post("/api/v1/auth/verify") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"$email","code":"$code"}"""
    }

    private fun newAccount(): Pair<String, String> {
        mailer.clear()
        val email = uniqueEmail()
        signUp(email)
        return email to mailer.lastCode()
    }

    @Test
    fun `signing up sends a six-digit code to the address given`() {
        mailer.clear()
        val email = uniqueEmail()
        signUp(email)

        assertEquals(1, mailer.sent.size)
        assertEquals(email, mailer.sent.single().first)
        assertTrue(mailer.lastCode().matches(Regex("\\d{6}")))
    }

    @Test
    fun `the right code marks the address verified`() {
        val (email, code) = newAccount()
        assertFalse(users.findByEmailIgnoringCase(email).get().emailVerified)

        verify(email, code).andExpect { status { isNoContent() } }

        assertTrue(users.findByEmailIgnoringCase(email).get().emailVerified)
    }

    @Test
    fun `a wrong code is refused`() {
        val (email, code) = newAccount()
        val wrong = if (code == "000000") "111111" else "000000"

        verify(email, wrong).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }
        assertFalse(users.findByEmailIgnoringCase(email).get().emailVerified)
    }

    @Test
    fun `a code cannot be spent twice`() {
        val (email, code) = newAccount()
        verify(email, code).andExpect { status { isNoContent() } }

        verify(email, code).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }
    }

    @Test
    fun `guessing is capped, and the code dies once the cap is passed`() {
        val (email, code) = newAccount()
        val wrong = if (code == "000000") "111111" else "000000"

        // maxAttempts is 5; the sixth guess is refused as a rate limit rather
        // than as a wrong code.
        repeat(5) {
            verify(email, wrong).andExpect { status { isUnauthorized() } }
        }
        verify(email, wrong).andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("TooManyAttempts") }
        }

        // And the real code is dead too, so burning the attempts cannot be
        // followed by a lucky guess.
        verify(email, code).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an unknown address is refused exactly like a wrong code`() {
        verify(uniqueEmail(), "123456").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }
    }

    @Test
    fun `resending an unknown address says nothing and sends nothing`() {
        mailer.clear()
        mockMvc.post("/api/v1/auth/verify/resend") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"${uniqueEmail()}"}"""
        }.andExpect { status { isNoContent() } }

        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun `sending again too soon is refused, so the endpoint cannot flood an inbox`() {
        val (email, _) = newAccount()

        val error = assertThrows<AuthException> {
            verification.resend(email, VerificationPurpose.VerifyEmail)
        }
        assertEquals(AuthErrorCode.TooManyAttempts, error.code)
    }

    @Test
    fun `the row stores a hash, not the digits`() {
        val (email, code) = newAccount()
        val userId = users.findByEmailIgnoringCase(email).get().requireId()

        val stored = codes
            .findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, VerificationPurpose.VerifyEmail)
            .get()

        // A dump of this table must not hand out working codes.
        assertNotEquals(code, stored.codeHash)
        assertTrue(stored.codeHash.startsWith("$2"), "expected a BCrypt hash, got ${stored.codeHash}")
    }
}
