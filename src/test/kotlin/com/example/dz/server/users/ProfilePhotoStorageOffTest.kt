package com.example.dz.server.users

import com.example.dz.server.users.repository.UserRepository
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/** With no storage configured (the prod default until one is), uploads say so instead of failing oddly. */
@SpringBootTest(properties = ["dz.storage.type=none"])
@AutoConfigureMockMvc
@Transactional
class ProfilePhotoStorageOffTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    @Test
    fun `uploads are refused as unavailable`() {
        val email = "user-${UUID.randomUUID()}@example.com"
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Amelia Hart","email":"$email","password":"correct-horse-battery"}"""
        }.andReturn().response.contentAsString
        users.findByEmailIgnoringCase(email).get().emailVerified = true
        val token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/avatar") {
            file(MockMultipartFile("image", "picture.png", MediaType.IMAGE_PNG_VALUE, ByteArray(10)))
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isEqualTo(503) }
            jsonPath("$.code") { value("PhotosUnavailable") }
        }
    }
}
