package com.example.dz.server.friends

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
class FriendTest {

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

    private fun sendRequest(token: String, targetUserId: UUID) =
        mockMvc.post("/api/v1/friends/requests") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetUserId":"$targetUserId"}"""
        }

    @Test
    fun `friends and friend requests require authentication`() {
        mockMvc.get("/api/v1/friends").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/friends/requests").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/friends/suggested").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/friends/${UUID.randomUUID()}").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/friends/requests") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetUserId":"${UUID.randomUUID()}"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a new account has no friends and no pending requests`() {
        val (_, token) = signUpAndGetToken()

        mockMvc.get("/api/v1/friends") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() }; jsonPath("$.friends.length()") { value(0) } }

        // A literal "/requests" segment, not a {friendId} -- confirms it isn't swallowed by getFriend.
        mockMvc.get("/api/v1/friends/requests") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() }; jsonPath("$.requests.length()") { value(0) } }
    }

    @Test
    fun `sending a request creates a pending request the addressee can see`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, bobToken) = signUpAndGetToken("Bob")

        sendRequest(aliceToken, bobId).andExpect {
            status { isCreated() }
            jsonPath("$.fromUser.id") { value(aliceId.toString()) }
        }

        mockMvc.get("/api/v1/friends/requests") { header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.requests.length()") { value(1) }
                jsonPath("$.requests[0].fromUser.name") { value("Alice") }
            }

        // Not yet accepted, so neither side shows up as a friend yet.
        mockMvc.get("/api/v1/friends") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { jsonPath("$.friends.length()") { value(0) } }
    }

    @Test
    fun `accepting a request makes both accounts friends`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, bobToken) = signUpAndGetToken("Bob")
        val requestId = requestIdOf(sendRequest(aliceToken, bobId))

        mockMvc.post("/api/v1/friends/requests/$requestId/accept") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(aliceId.toString()) }
        }

        mockMvc.get("/api/v1/friends") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { jsonPath("$.friends[0].id") { value(bobId.toString()) } }
        mockMvc.get("/api/v1/friends") { header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken") }
            .andExpect { jsonPath("$.friends[0].id") { value(aliceId.toString()) } }

        mockMvc.get("/api/v1/friends/$bobId") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { status { isOk() } }
        mockMvc.get("/api/v1/friends/requests") { header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken") }
            .andExpect { jsonPath("$.requests.length()") { value(0) } }
    }

    @Test
    fun `declining a request leaves both accounts unfriended, and a new request can follow`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, bobToken) = signUpAndGetToken("Bob")
        val requestId = requestIdOf(sendRequest(aliceToken, bobId))

        mockMvc.post("/api/v1/friends/requests/$requestId/decline") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/friends") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { jsonPath("$.friends.length()") { value(0) } }

        // A decline doesn't permanently block a future request between the same pair.
        sendRequest(aliceToken, bobId).andExpect { status { isCreated() } }
    }

    @Test
    fun `sending a request to yourself is rejected`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")

        sendRequest(aliceToken, aliceId).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("SelfRequest") }
        }
    }

    @Test
    fun `sending a request to a nonexistent or malformed id is rejected`() {
        val (_, aliceToken) = signUpAndGetToken("Alice")

        sendRequest(aliceToken, UUID.randomUUID()).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("UserNotFound") }
        }

        mockMvc.post("/api/v1/friends/requests") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetUserId":"not-a-uuid"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("InvalidTarget") }
        }
    }

    @Test
    fun `a duplicate pending request, or one to an existing friend, is rejected`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, bobToken) = signUpAndGetToken("Bob")

        sendRequest(aliceToken, bobId).andExpect { status { isCreated() } }
        sendRequest(aliceToken, bobId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("AlreadyPending") }
        }
        // The reverse direction is the same pending relationship, not a fresh one.
        sendRequest(bobToken, aliceId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("AlreadyPending") }
        }

        val requestId = requestIdOf(mockMvc.get("/api/v1/friends/requests") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken")
        })
        mockMvc.post("/api/v1/friends/requests/$requestId/accept") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken")
        }.andExpect { status { isOk() } }

        sendRequest(aliceToken, bobId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("AlreadyFriends") }
        }
    }

    @Test
    fun `a stranger cannot accept, decline, or see someone else's pending request`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, _) = signUpAndGetToken("Bob")
        val (_, strangerToken) = signUpAndGetToken("Carol")
        val requestId = requestIdOf(sendRequest(aliceToken, bobId))

        mockMvc.post("/api/v1/friends/requests/$requestId/accept") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken")
        }.andExpect { status { isNotFound() } }
        mockMvc.post("/api/v1/friends/requests/$requestId/decline") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken")
        }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/friends/requests") { header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken") }
            .andExpect { jsonPath("$.requests.length()") { value(0) } }
    }

    @Test
    fun `a non-friend's detail is a 404`() {
        val (_, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, _) = signUpAndGetToken("Bob")

        mockMvc.get("/api/v1/friends/$bobId") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("FriendNotFound") }
            }
    }

    @Test
    fun `suggested excludes the caller but includes another real account`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, _) = signUpAndGetToken("Bob")

        mockMvc.get("/api/v1/friends/suggested") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.users[?(@.id=='$aliceId')]") { doesNotExist() }
                jsonPath("$.users[?(@.id=='$bobId')]") { exists() }
            }
    }

    @Test
    fun `suggested excludes an already-accepted friend, for both sides`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, bobToken) = signUpAndGetToken("Bob")
        val requestId = requestIdOf(sendRequest(aliceToken, bobId))
        mockMvc.post("/api/v1/friends/requests/$requestId/accept") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken")
        }

        mockMvc.get("/api/v1/friends/suggested") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { jsonPath("$.users[?(@.id=='$bobId')]") { doesNotExist() } }
        mockMvc.get("/api/v1/friends/suggested") { header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken") }
            .andExpect { jsonPath("$.users[?(@.id=='$aliceId')]") { doesNotExist() } }
    }

    @Test
    fun `suggested excludes a pending request in either direction`() {
        val (aliceId, aliceToken) = signUpAndGetToken("Alice")
        val (bobId, bobToken) = signUpAndGetToken("Bob")
        sendRequest(aliceToken, bobId)

        // Alice sent it -- Bob isn't re-suggested to her.
        mockMvc.get("/api/v1/friends/suggested") { header(HttpHeaders.AUTHORIZATION, "Bearer $aliceToken") }
            .andExpect { jsonPath("$.users[?(@.id=='$bobId')]") { doesNotExist() } }
        // Bob received it -- Alice doesn't show up as a fresh suggestion; she's in his inbox instead.
        mockMvc.get("/api/v1/friends/suggested") { header(HttpHeaders.AUTHORIZATION, "Bearer $bobToken") }
            .andExpect { jsonPath("$.users[?(@.id=='$aliceId')]") { doesNotExist() } }
    }

    private fun requestIdOf(result: ResultActionsDsl): String {
        val body = result.andReturn().response.contentAsString
        return Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }
}
