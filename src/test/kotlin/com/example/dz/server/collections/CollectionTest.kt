package com.example.dz.server.collections

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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CollectionTest {

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

    private fun save(token: String, id: String, body: String) =
        mockMvc.put("/api/v1/collections/$id") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun book(id: String, title: String) =
        """{"bookId":"$id","title":"$title","author":"Someone","coverUrl":"https://example.com/$id.jpg"}"""

    @Test
    fun `collections require authentication`() {
        mockMvc.get("/api/v1/collections").andExpect { status { isUnauthorized() } }
        mockMvc.delete("/api/v1/collections/anything").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `creating a collection under a client-chosen id stores it with its books`() {
        val token = signUpAndGetToken()

        save(
            token, "science-fiction",
            """{"title":"Science Fiction","description":"The good stuff","books":[${book("g-84", "Frankenstein")}]}""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.id") { value("science-fiction") }
            jsonPath("$.title") { value("Science Fiction") }
            jsonPath("$.description") { value("The good stuff") }
            jsonPath("$.books.length()") { value(1) }
            jsonPath("$.books[0].bookId") { value("g-84") }
            jsonPath("$.books[0].coverUrl") { value("https://example.com/g-84.jpg") }
        }

        mockMvc.get("/api/v1/collections/science-fiction") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.books.length()") { value(1) }
        }
    }

    @Test
    fun `saving again replaces the membership wholesale`() {
        val token = signUpAndGetToken()

        save(token, "shelf", """{"title":"Shelf","books":[${book("a", "A")},${book("b", "B")}]}""")
            .andExpect { status { isOk() } }

        // "b" is kept, "a" drops out, "c" is new — the tricky case, because a
        // naive clear-and-reinsert would collide on the surviving row.
        save(token, "shelf", """{"title":"Shelf","books":[${book("b", "B")},${book("c", "C")}]}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.books.length()") { value(2) }
                jsonPath("$.books[0].bookId") { value("b") }
                jsonPath("$.books[1].bookId") { value("c") }
            }

        mockMvc.get("/api/v1/collections") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.collections.length()") { value(1) }
            jsonPath("$.collections[0].books.length()") { value(2) }
        }
    }

    @Test
    fun `an empty book list empties the collection`() {
        val token = signUpAndGetToken()

        save(token, "shelf", """{"title":"Shelf","books":[${book("a", "A")}]}""")
            .andExpect { status { isOk() } }
        save(token, "shelf", """{"title":"Shelf","books":[]}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.books.length()") { value(0) }
            }
    }

    @Test
    fun `book order is preserved`() {
        val token = signUpAndGetToken()

        save(
            token, "ordered",
            """{"title":"Ordered","books":[${book("third", "C")},${book("first", "A")},${book("second", "B")}]}""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.books[0].bookId") { value("third") }
            jsonPath("$.books[1].bookId") { value("first") }
            jsonPath("$.books[2].bookId") { value("second") }
        }
    }

    @Test
    fun `the same book sent twice is stored once instead of failing`() {
        val token = signUpAndGetToken()

        save(token, "dupes", """{"title":"Dupes","books":[${book("a", "A")},${book("a", "A again")}]}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.books.length()") { value(1) }
            }
    }

    @Test
    fun `listing several collections returns each once with its own books`() {
        val token = signUpAndGetToken()

        save(token, "one", """{"title":"One","books":[${book("a", "A")},${book("b", "B")}]}""")
            .andExpect { status { isOk() } }
        save(token, "two", """{"title":"Two","books":[${book("c", "C")}]}""")
            .andExpect { status { isOk() } }

        // A join fetch that is not de-duplicated would report three or four here.
        mockMvc.get("/api/v1/collections") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.collections.length()") { value(2) }
        }
    }

    @Test
    fun `deleting removes the collection, and deleting again is a 404`() {
        val token = signUpAndGetToken()
        save(token, "shelf", """{"title":"Shelf","books":[${book("a", "A")}]}""")
            .andExpect { status { isOk() } }

        mockMvc.delete("/api/v1/collections/shelf") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        mockMvc.delete("/api/v1/collections/shelf") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CollectionNotFound") }
        }

        mockMvc.get("/api/v1/collections") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { jsonPath("$.collections.length()") { value(0) } }
    }

    @Test
    fun `another account cannot read, overwrite or delete a collection`() {
        val owner = signUpAndGetToken()
        val stranger = signUpAndGetToken()

        save(owner, "private-shelf", """{"title":"Mine","books":[${book("a", "A")}]}""")
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/collections/private-shelf") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $stranger")
        }.andExpect { status { isNotFound() } }

        mockMvc.delete("/api/v1/collections/private-shelf") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $stranger")
        }.andExpect { status { isNotFound() } }

        // Saving under the same id creates the stranger's own collection rather
        // than touching the owner's.
        save(stranger, "private-shelf", """{"title":"Also mine","books":[]}""")
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/collections/private-shelf") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Mine") }
            jsonPath("$.books.length()") { value(1) }
        }
    }

    @Test
    fun `rejects a blank collection title and a book without an id`() {
        val token = signUpAndGetToken()

        save(token, "bad", """{"title":"   ","books":[]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.title") { exists() }
        }

        save(token, "bad", """{"title":"Fine","books":[{"bookId":"","title":"No id"}]}""")
            .andExpect { status { isBadRequest() } }
    }
}
