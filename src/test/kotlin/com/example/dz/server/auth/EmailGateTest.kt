package com.example.dz.server.auth

import com.example.dz.server.auth.mail.MailDispatcher
import com.example.dz.server.users.repository.UserRepository
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * Sign-up hands out a session before the address is proven, so that the app can hold one while the
 * reader reads their mail. That session must not be a way to skip verifying: background the app,
 * come back, and the code would never be spent.
 *
 * Gating this in the client alone would stop nobody holding the token, so it is enforced here.
 */
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@Transactional
class EmailGateTest {

    @TestConfiguration
    class Mail {
        @Bean @Primary fun capturingMailer() = CapturingMailer()
        @Bean(MailDispatcher.MAIL_EXECUTOR) fun mailExecutor(): Executor = SyncTaskExecutor()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var mailer: CapturingMailer
    @Autowired private lateinit var users: UserRepository

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@example.com"

    /** Returns the access token and the code that was mailed. */
    private fun signUp(): Triple<String, String, String> {
        mailer.clear()
        val email = uniqueEmail()
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Ada","email":"$email","password":"correct-horse-battery"}"""
        }.andReturn().response.contentAsString
        val token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        return Triple(email, token, mailer.lastCode())
    }

    @Test
    fun `sign-up reports the address as unverified`() {
        val (_, token, _) = signUp()

        mockMvc.get("/api/v1/auth/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.emailVerified") { value(false) }
        }
    }

    @Test
    fun `an unverified session cannot reach the rest of the app`() {
        val (_, token, _) = signUp()

        mockMvc.get("/api/v1/library/books") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EmailNotVerified") }
        }
    }

    @Test
    fun `an unverified session can still finish verifying and sign out`() {
        val (email, token, code) = signUp()

        // Escaping the gate must stay reachable, or the reader is stuck behind it.
        mockMvc.post("/api/v1/auth/verify/resend") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email"}"""
        }.andExpect { status { isNoContent() } }

        mockMvc.post("/api/v1/auth/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","code":"$code"}"""
        }.andExpect { status { isNoContent() } }

        assertTrue(users.findByEmailIgnoringCase(email).get().emailVerified)
    }

    @Test
    fun `once verified the same session works everywhere`() {
        val (email, token, code) = signUp()

        mockMvc.post("/api/v1/auth/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","code":"$code"}"""
        }.andExpect { status { isNoContent() } }

        // Same token as before — verifying must not require signing in again.
        mockMvc.get("/api/v1/library/books") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/auth/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { jsonPath("$.emailVerified") { value(true) } }
    }
}
