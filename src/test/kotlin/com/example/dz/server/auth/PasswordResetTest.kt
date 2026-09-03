package com.example.dz.server.auth

import com.example.dz.server.auth.entity.VerificationPurpose
import com.example.dz.server.auth.mail.MailDispatcher
import com.example.dz.server.auth.repository.VerificationCodeRepository
import com.example.dz.server.users.repository.UserRepository
import java.util.concurrent.Executor
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * Resetting a forgotten password: the code proves the mailbox is read, the new password takes
 * effect, the old one stops working, and every session opened with the old one is gone.
 *
 * The rules the code itself obeys — expiry, attempt cap, single use — are [EmailVerificationTest]'s
 * to pin, because both purposes share one implementation. What is tested here is what a *reset*
 * does on top of them.
 *
 * Needs the local Postgres from docker-compose, like [AuthFlowTest].
 */
@SpringBootTest(
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
@AutoConfigureMockMvc
@Transactional
class PasswordResetTest {

    @TestConfiguration
    class Mail {
        @Bean
        @Primary
        fun capturingMailer() = CapturingMailer()

        /** Inline, so assertions do not race the send. See [EmailVerificationTest.Mail]. */
        @Bean(MailDispatcher.MAIL_EXECUTOR)
        fun mailExecutor(): Executor = SyncTaskExecutor()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var mailer: CapturingMailer
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var codes: VerificationCodeRepository

    private val oldPassword = "correct-horse-battery"
    private val newPassword = "a-different-long-one"

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@example.com"

    private fun signUp(email: String, password: String = oldPassword) =
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Ada Lovelace","email":"$email","password":"$password"}"""
        }.andReturn().response.contentAsString

    private fun forgot(email: String) = mockMvc.post("/api/v1/auth/password/forgot") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"$email"}"""
    }

    private fun reset(email: String, code: String, password: String = newPassword) =
        mockMvc.post("/api/v1/auth/password/reset") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","code":"$code","newPassword":"$password"}"""
        }

    private fun login(email: String, password: String) = mockMvc.post("/api/v1/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"$email","password":"$password"}"""
    }

    /** An account with a reset code outstanding, and the code a reader would have. */
    private fun accountAwaitingReset(): Pair<String, String> {
        val email = uniqueEmail()
        signUp(email)
        mailer.clear()
        forgot(email).andExpect { status { isNoContent() } }
        return email to mailer.lastCode()
    }

    @Test
    fun `asking to reset mails a six-digit code`() {
        val email = uniqueEmail()
        signUp(email)
        mailer.clear()

        forgot(email).andExpect { status { isNoContent() } }

        assertEquals(1, mailer.sent.size)
        assertEquals(email, mailer.sent.single().first)
        assertTrue(mailer.lastCode().matches(Regex("\\d{6}")))
    }

    @Test
    fun `an address with no account is answered the same, and mailed nothing`() {
        mailer.clear()

        // 204 exactly as a registered address gets, or the endpoint becomes a way to
        // ask which emails have accounts.
        forgot(uniqueEmail()).andExpect { status { isNoContent() } }

        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun `the code sets the new password, and the old one stops working`() {
        val (email, code) = accountAwaitingReset()

        reset(email, code).andExpect { status { isNoContent() } }

        login(email, newPassword).andExpect { status { isOk() } }
        login(email, oldPassword).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }
    }

    @Test
    fun `a wrong code changes nothing`() {
        val (email, code) = accountAwaitingReset()
        val wrong = if (code == "000000") "111111" else "000000"

        reset(email, wrong).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }

        login(email, oldPassword).andExpect { status { isOk() } }
        login(email, newPassword).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a reset code works once`() {
        val (email, code) = accountAwaitingReset()
        reset(email, code).andExpect { status { isNoContent() } }

        // Otherwise anyone who saw the code once could keep taking the account back.
        reset(email, code, "yet-another-password").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }
        login(email, newPassword).andExpect { status { isOk() } }
    }

    @Test
    fun `resetting signs every other device out`() {
        val email = uniqueEmail()
        val refreshToken = Regex("\"refreshToken\":\"([^\"]+)\"")
            .find(signUp(email))!!.groupValues[1]
        mailer.clear()
        forgot(email).andExpect { status { isNoContent() } }

        reset(email, mailer.lastCode()).andExpect { status { isNoContent() } }

        // A reset is what someone does when they think the old password is known to
        // somebody else. A session that outlived it would be the thing they were escaping.
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refreshToken"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a confirmation code is no use against a password`() {
        val email = uniqueEmail()
        mailer.clear()
        signUp(email)
        // What sign-up mailed, which is a VerifyEmail code and nothing else.
        val confirmationCode = mailer.lastCode()

        reset(email, confirmationCode).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }
        login(email, oldPassword).andExpect { status { isOk() } }
    }

    @Test
    fun `the new password has to be a real one`() {
        val (email, code) = accountAwaitingReset()

        reset(email, code, "short").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("WeakPassword") }
        }
        login(email, oldPassword).andExpect { status { isOk() } }
    }

    @Test
    fun `a reset leaves an unproven address unproven`() {
        val (email, code) = accountAwaitingReset()

        reset(email, code).andExpect { status { isNoContent() } }

        // Reading the mailbox does prove the address, but saying so is
        // /auth/verify's job — kept in one place rather than two.
        assertEquals(false, users.findByEmailIgnoringCase(email).get().emailVerified)
    }

    @Test
    fun `a reset code is a reset code, not a confirmation`() {
        val (email, code) = accountAwaitingReset()

        mockMvc.post("/api/v1/auth/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","code":"$code"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }

        // And it still works for what it was issued for.
        reset(email, code).andExpect { status { isNoContent() } }
    }

    @Test
    fun `the reset code is stored as a hash`() {
        val (email, code) = accountAwaitingReset()
        val userId = users.findByEmailIgnoringCase(email).get().requireId()

        val stored = codes
            .findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, VerificationPurpose.ResetPassword)
            .get()

        // A dump of this table must not hand out working codes.
        assertNotEquals(code, stored.codeHash)
        assertTrue(stored.codeHash.startsWith("$2"), "expected a BCrypt hash, got ${stored.codeHash}")
    }
}
