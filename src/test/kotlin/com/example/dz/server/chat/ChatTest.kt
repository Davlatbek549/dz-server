package com.example.dz.server.chat

import com.example.dz.server.users.repository.UserRepository
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var users: UserRepository

    private fun signUpAndGetToken(name: String = "Reader"): Pair<UUID, String> {
        val email = "user-${UUID.randomUUID()}@example.com"
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$name","email":"$email","password":"correct-horse-battery"}"""
        }.andReturn().response.contentAsString
        val user = users.findByEmailIgnoringCase(email).get()
        user.emailVerified = true
        val token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        return user.id!! to token
    }

    /** Alice sends, Bob accepts -- the two are friends by the time this returns. */
    private fun befriend(aliceToken: String, bobToken: String, bobId: UUID) {
        val requestId = requestIdOf(
            mockMvc.post("/api/v1/friends/requests") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"targetUserId":"$bobId"}"""
            },
        )
        mockMvc.post("/api/v1/friends/requests/$requestId/accept") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken")
        }.andExpect { status { isOk() } }
    }

    private fun send(token: String, friendId: UUID, text: String) =
        mockMvc.post("/api/v1/messages/$friendId") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"text":"$text"}"""
        }

    private fun requestIdOf(result: ResultActionsDsl): String {
        val body = result.andReturn().response.contentAsString
        return Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    @Test
    fun `chats and messages require authentication`() {
        mockMvc.get("/api/v1/chats").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/messages/${UUID.randomUUID()}").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/messages/${UUID.randomUUID()}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"text":"hi"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a fresh friendship has no messages and an empty-preview chat entry`() {
        val (_, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, bobToken) = signUpAndGetToken("Bob")
        befriend(aliceToken, bobToken, bobId)

        mockMvc.get("/api/v1/chats") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.chats.length()") { value(1) }
                jsonPath("$.chats[0].friend.id") { value(bobId.toString()) }
                jsonPath("$.chats[0].unreadCount") { value(0) }
            }
        mockMvc.get("/api/v1/messages/$bobId") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { jsonPath("$.messages.length()") { value(0) } }
    }

    @Test
    fun `a message sent between friends is visible to both, and tracked as unread until read`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, bobToken) = signUpAndGetToken("Bob")
        befriend(aliceToken, bobToken, bobId)

        send(aliceToken, bobId, "hello bob").andExpect {
            status { isCreated() }
            jsonPath("$.senderId") { value(aliceId.toString()) }
            jsonPath("$.text") { value("hello bob") }
        }

        // Before anyone opens the thread: Bob has one unread message, Alice (the sender) has none.
        mockMvc.get("/api/v1/chats") { header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken") }
            .andExpect {
                jsonPath("$.chats[0].lastMessage.text") { value("hello bob") }
                jsonPath("$.chats[0].unreadCount") { value(1) }
            }
        mockMvc.get("/api/v1/chats") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { jsonPath("$.chats[0].unreadCount") { value(0) } }

        // Opening the thread, from either side, is visible -- and marks Bob's copy read.
        mockMvc.get("/api/v1/messages/$aliceId") { header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.messages.length()") { value(1) }
                jsonPath("$.messages[0].text") { value("hello bob") }
            }
        mockMvc.get("/api/v1/messages/$bobId") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { jsonPath("$.messages.length()") { value(1) } }

        mockMvc.get("/api/v1/chats") { header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken") }
            .andExpect { jsonPath("$.chats[0].unreadCount") { value(0) } }
    }

    @Test
    fun `a non-friend cannot send or read messages`() {
        val (_, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, _) = signUpAndGetToken("Bob")

        send(aliceToken, bobId, "hi").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("FriendNotFound") }
        }
        mockMvc.get("/api/v1/messages/$bobId") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { status { isNotFound() } }
    }
}
