package com.example.dz.server.library

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LibraryTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun signUpAndGetToken(): String {
        val email = "user-${UUID.randomUUID()}@example.com"
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Reader","email":"$email","password":"correct-horse-battery"}"""
        }.andReturn().response.contentAsString
        return Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun addBook(
        token: String,
        bookId: String,
        title: String = "Frankenstein",
        progress: Int = 0,
        favorite: Boolean = false,
    ) = mockMvc.put("/api/v1/library/books/$bookId") {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """
            {
              "title":"$title",
              "author":"Mary Shelley",
              "coverUrl":"https://example.com/cover.jpg",
              "textUrl":"https://example.com/text.txt",
              "isFree":true,
              "isFavorite":$favorite,
              "progressPercent":$progress
            }
        """.trimIndent()
    }

    @Test
    fun `library requires authentication`() {
        mockMvc.get("/api/v1/library/books").andExpect { status { isUnauthorized() } }
        mockMvc.delete("/api/v1/library/books/anything").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a new library is empty`() {
        val token = signUpAndGetToken()

        mockMvc.get("/api/v1/library/books") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.books") { isArray() }
            jsonPath("$.books.length()") { value(0) }
        }
    }

    @Test
    fun `adding a book puts it in the library`() {
        val token = signUpAndGetToken()

        addBook(token, "gutendex-84").andExpect {
            status { isOk() }
            jsonPath("$.bookId") { value("gutendex-84") }
            jsonPath("$.title") { value("Frankenstein") }
            jsonPath("$.author") { value("Mary Shelley") }
            jsonPath("$.progressPercent") { value(0) }
            jsonPath("$.addedAt") { exists() }
            // Device-local state is never synced.
            jsonPath("$.isDownloaded") { doesNotExist() }
            jsonPath("$.downloadPath") { doesNotExist() }
        }

        mockMvc.get("/api/v1/library/books") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.books.length()") { value(1) }
            jsonPath("$.books[0].bookId") { value("gutendex-84") }
        }
    }

    @Test
    fun `adding the same book twice refreshes it instead of duplicating`() {
        val token = signUpAndGetToken()

        addBook(token, "gutendex-84", title = "Frankenstein").andExpect { status { isOk() } }
        addBook(token, "gutendex-84", title = "Frankenstein (1818 text)").andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Frankenstein (1818 text)") }
        }

        mockMvc.get("/api/v1/library/books") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.books.length()") { value(1) }
        }
    }

    @Test
    fun `updating progress records when it was last read`() {
        val token = signUpAndGetToken()
        addBook(token, "gutendex-84").andExpect { status { isOk() } }

        mockMvc.patch("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"progressPercent":42}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.progressPercent") { value(42) }
            jsonPath("$.lastReadAt") { exists() }
            jsonPath("$.isFavorite") { value(false) }
        }
    }

    @Test
    fun `updating only the favourite flag leaves progress alone`() {
        val token = signUpAndGetToken()
        addBook(token, "gutendex-84", progress = 30).andExpect { status { isOk() } }

        mockMvc.patch("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"isFavorite":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.isFavorite") { value(true) }
            jsonPath("$.progressPercent") { value(30) }
        }
    }

    @Test
    fun `removing a book takes it out, and removing it again is a 404`() {
        val token = signUpAndGetToken()
        addBook(token, "gutendex-84").andExpect { status { isOk() } }

        mockMvc.delete("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        mockMvc.delete("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("BookNotFound") }
        }
    }

    @Test
    fun `continue reading returns the part-read book and nothing when there is none`() {
        val token = signUpAndGetToken()

        // Nothing started yet.
        mockMvc.get("/api/v1/library/books/continue-reading") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        addBook(token, "unread", progress = 0).andExpect { status { isOk() } }
        addBook(token, "finished", progress = 100).andExpect { status { isOk() } }
        addBook(token, "in-progress", progress = 55).andExpect { status { isOk() } }

        // Only the started-but-unfinished one qualifies.
        mockMvc.get("/api/v1/library/books/continue-reading") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.bookId") { value("in-progress") }
            jsonPath("$.progressPercent") { value(55) }
        }
    }

    @Test
    fun `one user's book is invisible and untouchable from another account`() {
        val owner = signUpAndGetToken()
        val stranger = signUpAndGetToken()

        addBook(owner, "gutendex-84").andExpect { status { isOk() } }

        // "Not yours" is indistinguishable from "does not exist".
        mockMvc.get("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $stranger")
        }.andExpect { status { isNotFound() } }

        mockMvc.patch("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $stranger")
            contentType = MediaType.APPLICATION_JSON
            content = """{"progressPercent":99}"""
        }.andExpect { status { isNotFound() } }

        mockMvc.delete("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $stranger")
        }.andExpect { status { isNotFound() } }

        // The stranger's library stayed empty, and the owner's book is untouched.
        mockMvc.get("/api/v1/library/books") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $stranger")
        }.andExpect { jsonPath("$.books.length()") { value(0) } }

        mockMvc.get("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.progressPercent") { value(0) }
        }
    }

    @Test
    fun `the same book can sit in two libraries independently`() {
        val first = signUpAndGetToken()
        val second = signUpAndGetToken()

        addBook(first, "gutendex-84", progress = 10).andExpect { status { isOk() } }
        addBook(second, "gutendex-84", progress = 90).andExpect { status { isOk() } }

        mockMvc.get("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $first")
        }.andExpect { jsonPath("$.progressPercent") { value(10) } }

        mockMvc.get("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $second")
        }.andExpect { jsonPath("$.progressPercent") { value(90) } }
    }

    @Test
    fun `rejects impossible progress and a missing title`() {
        val token = signUpAndGetToken()
        addBook(token, "gutendex-84").andExpect { status { isOk() } }

        mockMvc.patch("/api/v1/library/books/gutendex-84") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"progressPercent":101}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.progressPercent") { exists() }
        }

        mockMvc.put("/api/v1/library/books/no-title") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"  "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.title") { exists() }
        }
    }
}
