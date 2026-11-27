package com.example.dz.server.users

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
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** The fields the app's Edit Profile screen added on top of [UserProfileTest]'s. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EditProfileFieldsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var users: UserRepository

    private fun signUpAndGetToken(
        name: String = "Amelia Hart",
        email: String = "user-${UUID.randomUUID()}@example.com",
    ): String {
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$name","email":"$email","password":"correct-horse-battery"}"""
        }.andReturn().response.contentAsString
        users.findByEmailIgnoringCase(email).get().emailVerified = true
        return Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun put(token: String, json: String): ResultActionsDsl =
        mockMvc.put("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = json
        }

    private fun availability(token: String, username: String): ResultActionsDsl =
        mockMvc.get("/api/v1/users/username-availability") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            param("username", username)
        }

    /** A unique, valid username for one test, so tests never collide through the shared table. */
    private fun freshUsername(prefix: String = "reader"): String =
        "$prefix${UUID.randomUUID().toString().replace("-", "").take(12)}"

    @Test
    fun `a new profile starts open, with nothing else set`() {
        val token = signUpAndGetToken()

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.profileVisibility") { value("OPEN") }
            jsonPath("$.user.emailVerified") { value(true) }
            jsonPath("$.user.username") { doesNotExist() }
            jsonPath("$.dateOfBirth") { doesNotExist() }
            jsonPath("$.address") { doesNotExist() }
        }
    }

    @Test
    fun `every new field is saved and read back`() {
        val token = signUpAndGetToken()
        val username = freshUsername("Amelia.")

        put(
            token,
            """
            {
              "name":"Amelia Hart",
              "username":"$username",
              "phoneNumber":"+447700900123",
              "phoneCountry":"gb",
              "dateOfBirth":"1999-03-14",
              "gender":"FEMALE",
              "specialityKind":"WORK",
              "speciality":"  Book editor at a small press ",
              "address":{"country":"gb","city":"London","street":"14 Elm Row, Flat 3","postalCode":"N1 7GU"},
              "profileVisibility":"PRIVATE"
            }
            """.trimIndent(),
        ).andExpect { status { isOk() } }

        mockMvc.get("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            // Stored lower case, whatever case it was typed in.
            jsonPath("$.user.username") { value(username.lowercase()) }
            jsonPath("$.phoneCountry") { value("GB") }
            jsonPath("$.dateOfBirth") { value("1999-03-14") }
            jsonPath("$.gender") { value("FEMALE") }
            jsonPath("$.specialityKind") { value("WORK") }
            jsonPath("$.speciality") { value("Book editor at a small press") }
            jsonPath("$.address.country") { value("GB") }
            jsonPath("$.address.city") { value("London") }
            jsonPath("$.address.postalCode") { value("N1 7GU") }
            jsonPath("$.profileVisibility") { value("PRIVATE") }
        }
    }

    @Test
    fun `username, visibility, date of birth and gender survive an edit that leaves them out`() {
        val token = signUpAndGetToken()
        val username = freshUsername()
        put(
            token,
            """{"name":"Amelia","username":"$username","profileVisibility":"PRIVATE","dateOfBirth":"1999-03-14","gender":"FEMALE",
               "address":{"city":"London"}}""",
        ).andExpect { status { isOk() } }

        put(token, """{"name":"Amelia Hart"}""").andExpect {
            status { isOk() }
            jsonPath("$.user.username") { value(username) }
            jsonPath("$.profileVisibility") { value("PRIVATE") }
            jsonPath("$.dateOfBirth") { value("1999-03-14") }
            jsonPath("$.gender") { value("FEMALE") }
            // Ordinary fields follow the full-replacement rule: omitted means cleared.
            jsonPath("$.address") { doesNotExist() }
        }
    }

    @Test
    fun `a username someone else holds is refused, in any case`() {
        val username = freshUsername()
        put(signUpAndGetToken(), """{"name":"First","username":"$username"}""").andExpect { status { isOk() } }

        put(signUpAndGetToken(), """{"name":"Second","username":"${username.uppercase()}"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("UsernameTaken") }
        }
    }

    @Test
    fun `saving your own username again is not a conflict`() {
        val token = signUpAndGetToken()
        val username = freshUsername()
        put(token, """{"name":"Amelia","username":"$username"}""").andExpect { status { isOk() } }
        put(token, """{"name":"Amelia","username":"$username","bio":"Changed"}""").andExpect { status { isOk() } }
    }

    @Test
    fun `a username with characters it may not hold is refused`() {
        put(signUpAndGetToken(), """{"name":"Amelia","username":"amelia hart!"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ValidationFailed") }
            jsonPath("$.fieldErrors.username") { exists() }
        }
    }

    @Test
    fun `date of birth can be set once, and then only repeated`() {
        val token = signUpAndGetToken()
        put(token, """{"name":"Amelia","dateOfBirth":"1999-03-14"}""").andExpect { status { isOk() } }

        // The app sends the whole profile every time, so the same value must keep passing.
        put(token, """{"name":"Amelia","dateOfBirth":"1999-03-14"}""").andExpect { status { isOk() } }

        put(token, """{"name":"Amelia","dateOfBirth":"2001-01-01"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DateOfBirthLocked") }
        }
    }

    @Test
    fun `gender can be set once, and then only repeated`() {
        val token = signUpAndGetToken()
        put(token, """{"name":"Amelia","gender":"FEMALE"}""").andExpect { status { isOk() } }
        put(token, """{"name":"Amelia","gender":"FEMALE"}""").andExpect { status { isOk() } }

        put(token, """{"name":"Amelia","gender":"MALE"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("GenderLocked") }
        }
    }

    @Test
    fun `a date of birth in the future or before 1900 is refused`() {
        val token = signUpAndGetToken()
        put(token, """{"name":"Amelia","dateOfBirth":"2999-01-01"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.fieldErrors.dateOfBirth") { exists() }
        }
        put(token, """{"name":"Amelia","dateOfBirth":"1850-01-01"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("InvalidDateOfBirth") }
        }
    }

    @Test
    fun `an unknown gender is refused rather than stored`() {
        put(signUpAndGetToken(), """{"name":"Amelia","gender":"SOMETHING_ELSE"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ValidationFailed") }
        }
    }

    @Test
    fun `availability says a free username is free and normalises what was typed`() {
        val username = freshUsername()
        availability(signUpAndGetToken(), "  @${username.uppercase()} ").andExpect {
            status { isOk() }
            jsonPath("$.username") { value(username) }
            jsonPath("$.available") { value(true) }
            jsonPath("$.reason") { doesNotExist() }
        }
    }

    @Test
    fun `availability offers free alternatives for a taken username`() {
        val username = freshUsername("amelia")
        put(signUpAndGetToken(), """{"name":"Someone","username":"$username"}""").andExpect { status { isOk() } }

        availability(signUpAndGetToken(name = "Amelia Hart"), username).andExpect {
            status { isOk() }
            jsonPath("$.available") { value(false) }
            jsonPath("$.reason") { value("Taken") }
            jsonPath("$.suggestions.length()") { value(2) }
            jsonPath("$.suggestions[0]") { value("$username.hart") }
            jsonPath("$.suggestions[1]") { value("${username}_reads") }
        }
    }

    @Test
    fun `availability treats your own username as available to you`() {
        val token = signUpAndGetToken()
        val username = freshUsername()
        put(token, """{"name":"Amelia","username":"$username"}""").andExpect { status { isOk() } }

        availability(token, username).andExpect {
            status { isOk() }
            jsonPath("$.available") { value(true) }
        }
    }

    @Test
    fun `availability calls a malformed username invalid`() {
        availability(signUpAndGetToken(), "a!").andExpect {
            status { isOk() }
            jsonPath("$.available") { value(false) }
            jsonPath("$.reason") { value("Invalid") }
            jsonPath("$.suggestions.length()") { value(0) }
        }
    }

    @Test
    fun `availability requires authentication`() {
        mockMvc.get("/api/v1/users/username-availability") {
            param("username", "amelia")
        }.andExpect { status { isUnauthorized() } }
    }
}
