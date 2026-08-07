package com.example.dz.server.users

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserProfileTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    /** Registers a user and returns their access token. */
    private fun signUpAndGetToken(email: String = "user-${UUID.randomUUID()}@example.com"): String {
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Ada Lovelace","email":"$email","password":"correct-horse-battery"}"""
        }.andReturn().response.contentAsString
        return Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    @Test
    fun `profile requires authentication`() {
        mockMvc.get("/api/v1/users/me").andExpect { status { isUnauthorized() } }
        mockMvc.put("/api/v1/users/me") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Whoever"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `profile returns the shape the app's UserProfile expects`() {
        val email = "user-${UUID.randomUUID()}@example.com"
        val token = signUpAndGetToken(email)

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.id") { exists() }
            jsonPath("$.user.name") { value("Ada Lovelace") }
            jsonPath("$.user.email") { value(email) }
            // Counts are owned by modules that do not exist yet.
            jsonPath("$.booksRead") { value(0) }
            jsonPath("$.friendsCount") { value(0) }
            jsonPath("$.collectionsCount") { value(0) }
            // A password hash must never appear in a profile payload.
            jsonPath("$.user.passwordHash") { doesNotExist() }
            jsonPath("$.passwordHash") { doesNotExist() }
        }
    }

    @Test
    fun `update writes every editable field and the change is visible afterwards`() {
        val token = signUpAndGetToken()

        mockMvc.put("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "name":"Ada B. Lovelace",
                  "avatarUrl":"https://example.com/ada.png",
                  "bio":"Analytical engine enthusiast",
                  "phoneNumber":"+441234567890",
                  "language":"en-GB",
                  "currentGoalMinutes":45
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.name") { value("Ada B. Lovelace") }
            jsonPath("$.bio") { value("Analytical engine enthusiast") }
            jsonPath("$.currentGoalMinutes") { value(45) }
        }

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.name") { value("Ada B. Lovelace") }
            jsonPath("$.user.avatarUrl") { value("https://example.com/ada.png") }
            jsonPath("$.phoneNumber") { value("+441234567890") }
            jsonPath("$.language") { value("en-GB") }
            jsonPath("$.currentGoalMinutes") { value(45) }
        }
    }

    @Test
    fun `update cannot change the email or the password`() {
        val email = "user-${UUID.randomUUID()}@example.com"
        val token = signUpAndGetToken(email)

        // Extra fields are not part of the request contract and must be ignored,
        // not quietly applied.
        mockMvc.put("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "name":"Ada",
                  "email":"attacker@example.com",
                  "password":"hunter2",
                  "passwordHash":"whatever",
                  "enabled":false
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.email") { value(email) }
        }

        // The original password still works, and the account is still enabled.
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"correct-horse-battery"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `update rejects a blank name`() {
        val token = signUpAndGetToken()

        mockMvc.put("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"   "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ValidationFailed") }
            jsonPath("$.fieldErrors.name") { exists() }
        }
    }

    @Test
    fun `update rejects an out-of-range reading goal`() {
        val token = signUpAndGetToken()

        mockMvc.put("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Ada","currentGoalMinutes":100000}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.currentGoalMinutes") { exists() }
        }
    }

    @Test
    fun `each token only ever reaches its own profile`() {
        val firstEmail = "user-${UUID.randomUUID()}@example.com"
        val secondEmail = "user-${UUID.randomUUID()}@example.com"
        val firstToken = signUpAndGetToken(firstEmail)
        val secondToken = signUpAndGetToken(secondEmail)

        mockMvc.put("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $secondToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Second User","bio":"Second bio"}"""
        }.andExpect { status { isOk() } }

        // The second user's write must not be visible to the first.
        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $firstToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.email") { value(firstEmail) }
            jsonPath("$.user.name") { value("Ada Lovelace") }
            jsonPath("$.bio") { doesNotExist() }
        }
    }
}
