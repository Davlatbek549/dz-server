package com.example.dz.server.notifications

import com.example.dz.server.notifications.entity.Notification
import com.example.dz.server.notifications.entity.NotificationCategory
import com.example.dz.server.notifications.repository.NotificationRepository
import com.example.dz.server.users.repository.UserRepository
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var notifications: NotificationRepository

    private fun signUpAndGetToken(): Pair<UUID, String> {
        val email = "user-${UUID.randomUUID()}@example.com"
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Reader","email":"$email","password":"correct-horse-battery"}"""
        }.andReturn().response.contentAsString
        // These tests are about what sits behind the gate, not the gate itself, so the address is
        // marked proven rather than each of them spending a code. EmailGateTest covers the gate.
        val user = users.findByEmailIgnoringCase(email).get()
        user.emailVerified = true
        val token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        return user.id!! to token
    }

    private fun seed(
        userId: UUID,
        category: NotificationCategory,
        title: String = "Notification",
        message: String = "Something happened",
    ) = notifications.save(Notification(userId = userId, category = category, title = title, message = message))

    @Test
    fun `notifications require authentication`() {
        mockMvc.get("/api/v1/notifications").andExpect { status { isUnauthorized() } }
        mockMvc.patch("/api/v1/notifications/${UUID.randomUUID()}/read")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a new account has no notifications`() {
        val (_, token) = signUpAndGetToken()

        mockMvc.get("/api/v1/notifications") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.notifications") { isArray() }
            jsonPath("$.notifications.length()") { value(0) }
        }
    }

    @Test
    fun `listing returns a user's notifications, newest first, with category`() {
        val (userId, token) = signUpAndGetToken()

        seed(userId, NotificationCategory.Friends, title = "Jonas started following you")
        Thread.sleep(5)
        seed(userId, NotificationCategory.Store, title = "Bestiary dropped to \$6.40")

        mockMvc.get("/api/v1/notifications") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.notifications.length()") { value(2) }
            jsonPath("$.notifications[0].title") { value("Bestiary dropped to \$6.40") }
            jsonPath("$.notifications[0].category") { value("Store") }
            jsonPath("$.notifications[0].isRead") { value(false) }
            jsonPath("$.notifications[1].category") { value("Friends") }
        }
    }

    @Test
    fun `marking a notification read updates it and sticks`() {
        val (userId, token) = signUpAndGetToken()
        val notification = seed(userId, NotificationCategory.Goals)

        mockMvc.patch("/api/v1/notifications/${notification.id}/read") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.isRead") { value(true) }
        }

        mockMvc.get("/api/v1/notifications") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            jsonPath("$.notifications[0].isRead") { value(true) }
        }
    }

    @Test
    fun `an unknown notification id is a 404`() {
        val (_, token) = signUpAndGetToken()

        mockMvc.patch("/api/v1/notifications/${UUID.randomUUID()}/read") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NotificationNotFound") }
        }
    }

    @Test
    fun `one user's notification is invisible and untouchable from another account`() {
        val (ownerId, _) = signUpAndGetToken()
        val (_, strangerToken) = signUpAndGetToken()
        val notification = seed(ownerId, NotificationCategory.Chat)

        mockMvc.patch("/api/v1/notifications/${notification.id}/read") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken")
        }.andExpect { status { isNotFound() } }

        mockMvc.get("/api/v1/notifications") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken")
        }.andExpect { jsonPath("$.notifications.length()") { value(0) } }
    }
}
