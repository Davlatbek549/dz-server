package com.example.dz.server.auth

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * Covers the flow the app depends on end to end. Needs the local Postgres from
 * docker-compose; Testcontainers would remove that requirement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@example.com"

    private fun signUp(
        email: String = uniqueEmail(),
        password: String = VALID_PASSWORD,
        name: String = "Ada Lovelace",
    ) = mockMvc.post("/api/v1/auth/signup") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"name":"$name","email":"$email","password":"$password"}"""
    }

    private fun tokensOf(body: String): Pair<String, String> {
        val access = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val refresh = Regex("\"refreshToken\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        return access to refresh
    }

    @Test
    fun `signup returns the shape the client expects`() {
        val email = uniqueEmail()
        signUp(email = email).andExpect {
            status { isCreated() }
            // Field names the app's AuthResponseDto reads.
            jsonPath("$.token") { exists() }
            jsonPath("$.user.id") { exists() }
            jsonPath("$.user.name") { value("Ada Lovelace") }
            jsonPath("$.user.email") { value(email) }
            // Extra fields, ignored by the current client build.
            jsonPath("$.refreshToken") { exists() }
            jsonPath("$.expiresIn") { value(900) }
            // The hash must never travel to a client.
            jsonPath("$.user.passwordHash") { doesNotExist() }
        }
    }

    @Test
    fun `signup rejects an email that is already registered`() {
        val email = uniqueEmail()
        signUp(email = email).andExpect { status { isCreated() } }

        signUp(email = email).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EmailAlreadyInUse") }
        }
    }

    @Test
    fun `signup rejects a duplicate email that differs only in case`() {
        val email = uniqueEmail()
        signUp(email = email).andExpect { status { isCreated() } }

        signUp(email = email.uppercase()).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EmailAlreadyInUse") }
        }
    }

    @Test
    fun `signup rejects a short password`() {
        signUp(password = "short").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("WeakPassword") }
        }
    }

    @Test
    fun `signup rejects a malformed email`() {
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Ada","email":"not-an-email","password":"$VALID_PASSWORD"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("InvalidEmail") }
        }
    }

    @Test
    fun `login succeeds regardless of email case`() {
        val email = uniqueEmail()
        signUp(email = email).andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"${email.uppercase()}","password":"$VALID_PASSWORD"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
        }
    }

    @Test
    fun `login gives the same answer for a wrong password and an unknown account`() {
        val email = uniqueEmail()
        signUp(email = email).andExpect { status { isCreated() } }

        val wrongPassword = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"not-the-password"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("InvalidCredentials") }
        }.andReturn().response.contentAsString

        val unknownAccount = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"${uniqueEmail()}","password":"$VALID_PASSWORD"}"""
        }.andExpect {
            status { isUnauthorized() }
        }.andReturn().response.contentAsString

        // Identical bodies, so responses cannot be used to discover which
        // addresses have accounts.
        assert(wrongPassword == unknownAccount) {
            "Responses differ and leak account existence:\n$wrongPassword\n$unknownAccount"
        }
    }

    @Test
    fun `protected endpoint rejects missing and malformed tokens with 401`() {
        mockMvc.get("/api/v1/auth/me").andExpect { status { isUnauthorized() } }

        mockMvc.get("/api/v1/auth/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.token")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `access token identifies the caller`() {
        val email = uniqueEmail()
        val body = signUp(email = email).andReturn().response.contentAsString
        val (accessToken, _) = tokensOf(body)

        mockMvc.get("/api/v1/auth/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(email) }
        }
    }

    @Test
    fun `refresh rotates the token and retires the old one`() {
        val body = signUp().andReturn().response.contentAsString
        val (_, firstRefresh) = tokensOf(body)

        val refreshed = mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$firstRefresh"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
        }.andReturn().response.contentAsString

        val (_, secondRefresh) = tokensOf(refreshed)
        assert(firstRefresh != secondRefresh) { "Refresh token was not rotated" }

        // Replaying the consumed token must fail.
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$firstRefresh"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `logout revokes the session so it cannot be refreshed`() {
        val body = signUp().andReturn().response.contentAsString
        val (accessToken, refreshToken) = tokensOf(body)

        mockMvc.post("/api/v1/auth/logout") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andExpect { status { isNoContent() } }

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refreshToken"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `logout requires authentication`() {
        mockMvc.post("/api/v1/auth/logout").andExpect { status { isUnauthorized() } }
    }

    private companion object {
        const val VALID_PASSWORD = "correct-horse-battery"
    }
}
