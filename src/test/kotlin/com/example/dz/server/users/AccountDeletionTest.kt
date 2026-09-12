package com.example.dz.server.users

import com.example.dz.server.auth.repository.RefreshTokenRepository
import com.example.dz.server.auth.repository.VerificationCodeRepository
import com.example.dz.server.collections.repository.BookCollectionRepository
import com.example.dz.server.library.repository.LibraryBookRepository
import com.example.dz.server.users.repository.UserRepository
import jakarta.persistence.EntityManager
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/**
 * `DELETE /api/v1/users/me`. The app stores require an app that makes accounts to let people
 * delete them from inside it; this is the half that actually removes anything.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountDeletionTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var refreshTokens: RefreshTokenRepository
    @Autowired private lateinit var verificationCodes: VerificationCodeRepository
    @Autowired private lateinit var library: LibraryBookRepository
    @Autowired private lateinit var collections: BookCollectionRepository
    @Autowired private lateinit var entityManager: EntityManager

    private data class Session(val email: String, val token: String, val refreshToken: String)

    private fun signUp(verified: Boolean = true): Session {
        val email = "user-${UUID.randomUUID()}@example.com"
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Ada Lovelace","email":"$email","password":"$PASSWORD"}"""
        }.andReturn().response.contentAsString
        if (verified) users.findByEmailIgnoringCase(email).get().emailVerified = true
        return Session(
            email = email,
            token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1],
            refreshToken = Regex("\"refreshToken\":\"([^\"]+)\"").find(body)!!.groupValues[1],
        )
    }

    private fun delete(token: String) =
        mockMvc.delete("/api/v1/users/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

    private fun count(table: String, userId: UUID): Long =
        entityManager.createNativeQuery("select count(*) from $table where user_id = :id")
            .setParameter("id", userId).singleResult as Long

    @Test
    fun `deleting the account removes it and everything it owns`() {
        val session = signUp()
        val userId = users.findByEmailIgnoringCase(session.email).get().requireId()
        mockMvc.put("/api/v1/library/books/g-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${session.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Frankenstein","author":"Mary Shelley","isFree":true}"""
        }.andExpect { status { isOk() } }
        mockMvc.put("/api/v1/collections/shelf") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${session.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Shelf","books":[{"bookId":"g-84","title":"Frankenstein","author":"Mary Shelley"}]}"""
        }.andExpect { status { isOk() } }
        // Every kind of thing an account can own, so the cascade is proven on each of them.
        assertTrue(count("refresh_tokens", userId) > 0)
        assertTrue(count("verification_codes", userId) > 0)
        assertEquals(1, count("library_books", userId))
        assertEquals(1, count("collections", userId))

        delete(session.token).andExpect { status { isNoContent() } }

        assertTrue(users.findById(userId).isEmpty, "the account itself")
        assertEquals(0, count("refresh_tokens", userId), "every session")
        assertEquals(0, count("verification_codes", userId), "outstanding codes")
        assertEquals(0, count("library_books", userId), "the library")
        assertEquals(0, count("collections", userId), "collections, and their books with them")
    }

    @Test
    fun `a deleted account can no longer sign in or refresh`() {
        val session = signUp()

        delete(session.token).andExpect { status { isNoContent() } }

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"${session.email}","password":"$PASSWORD"}"""
        }.andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"${session.refreshToken}"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an unverified account can delete itself, and nothing more`() {
        // An address mistyped at sign-up can never be verified; its owner still needs a way out.
        val session = signUp(verified = false)

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${session.token}")
        }.andExpect { status { isForbidden() } }

        delete(session.token).andExpect { status { isNoContent() } }
        assertTrue(users.findByEmailIgnoringCase(session.email).isEmpty)
    }

    @Test
    fun `deleting again succeeds`() {
        // A retry after a lost response must not come back as an error.
        val session = signUp()

        delete(session.token).andExpect { status { isNoContent() } }
        delete(session.token).andExpect { status { isNoContent() } }
    }

    @Test
    fun `deleting requires authentication`() {
        mockMvc.delete("/api/v1/users/me").andExpect { status { isUnauthorized() } }
    }

    private companion object {
        const val PASSWORD = "correct-horse-battery"
    }
}
