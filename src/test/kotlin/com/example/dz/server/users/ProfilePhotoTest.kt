package com.example.dz.server.users

import com.example.dz.server.users.repository.UserRepository
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

private const val UPLOADS = "build/test-uploads"
private const val BASE_URL = "http://files.test/files"
private const val TERRACOTTA = 0xFFC8784F.toInt()

/** `PUT` and `DELETE /api/v1/users/me/avatar` and `/banner`, stored in a local folder. */
@SpringBootTest(
    properties = [
        "dz.storage.type=local",
        "dz.storage.local-directory=$UPLOADS",
        "dz.storage.public-base-url=$BASE_URL",
    ],
)
@AutoConfigureMockMvc
@Transactional
class ProfilePhotoTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private data class Session(val email: String, val token: String)

    private fun signUp(): Session {
        val email = "user-${UUID.randomUUID()}@example.com"
        val body = mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Amelia Hart","email":"$email","password":"correct-horse-battery"}"""
        }.andReturn().response.contentAsString
        users.findByEmailIgnoringCase(email).get().emailVerified = true
        return Session(email, Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1])
    }

    private fun upload(
        token: String?,
        slot: String,
        bytes: ByteArray,
        fileName: String = "picture.png",
        type: String = MediaType.IMAGE_PNG_VALUE,
    ): ResultActionsDsl =
        mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/$slot") {
            file(MockMultipartFile("image", fileName, type, bytes))
            token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        }

    private fun remove(token: String, slot: String): ResultActionsDsl =
        mockMvc.delete("/api/v1/users/me/$slot") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }

    private fun ResultActionsDsl.storedUrl(field: String): String =
        Regex("\"$field\":\"([^\"]+)\"").find(andReturn().response.contentAsString)!!.groupValues[1]

    /** A flat-coloured picture, encoded the way a phone would send it. */
    private fun picture(width: Int, height: Int, format: String = "png", argb: Int = TERRACOTTA): ByteArray {
        val type = if (format == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val image = BufferedImage(width, height, type)
        val row = IntArray(width) { argb }
        for (y in 0 until height) image.setRGB(0, y, width, 1, row, 0, width)
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    /** Only a PNG's opening header: it claims [width] × [height] and holds no pixels at all. */
    private fun pngHeaderClaiming(width: Int, height: Int): ByteArray {
        val type = "IHDR".toByteArray()
        val header = ByteBuffer.allocate(13).putInt(width).putInt(height)
            .put(8).put(2).put(0).put(0).put(0).array()
        val crc = CRC32().apply { update(type); update(header) }.value.toInt()
        return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            ByteBuffer.allocate(4).putInt(header.size).array() + type + header +
            ByteBuffer.allocate(4).putInt(crc).array()
    }

    private fun fileOf(url: String): Path = Path.of(UPLOADS).toAbsolutePath().resolve(url.removePrefix("$BASE_URL/"))

    private fun stored(url: String): BufferedImage = ImageIO.read(fileOf(url).toFile())

    @Test
    fun `a profile photo is stored as a 512 pixel square JPEG`() {
        val session = signUp()

        val url = upload(session.token, "avatar", picture(900, 900)).andExpect {
            status { isOk() }
            jsonPath("$.user.name") { value("Amelia Hart") }
        }.storedUrl("avatarUrl")

        assertTrue(url.startsWith("$BASE_URL/avatars/") && url.endsWith(".jpg"), url)
        val bytes = Files.readAllBytes(fileOf(url))
        assertEquals(0xFF.toByte(), bytes[0], "a JPEG, whatever was sent")
        assertEquals(0xD8.toByte(), bytes[1])
        val image = stored(url)
        assertEquals(512, image.width)
        assertEquals(512, image.height)
    }

    @Test
    fun `a banner is stored at 1500 by 500`() {
        val session = signUp()

        val url = upload(session.token, "banner", picture(2400, 800, "jpeg"), "banner.jpg", MediaType.IMAGE_JPEG_VALUE)
            .andExpect { status { isOk() } }
            .storedUrl("bannerUrl")

        val image = stored(url)
        assertEquals(1500, image.width)
        assertEquals(500, image.height)
    }

    @Test
    fun `a picture a little off the shape is trimmed to it`() {
        val session = signUp()

        val url = upload(session.token, "banner", picture(1520, 500)).andExpect { status { isOk() } }.storedUrl("bannerUrl")

        assertEquals(1500, stored(url).width)
        assertEquals(500, stored(url).height)
    }

    @Test
    fun `the file's contents decide what it is, not its name`() {
        val session = signUp()

        upload(session.token, "avatar", picture(600, 600, "png"), "photo.jpg", MediaType.IMAGE_JPEG_VALUE)
            .andExpect { status { isOk() } }
    }

    @Test
    fun `transparent parts come out white`() {
        val session = signUp()

        val url = upload(session.token, "avatar", picture(600, 600, argb = 0x00000000)).storedUrl("avatarUrl")

        val pixel = stored(url).getRGB(256, 256)
        listOf(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF).forEach { channel ->
            assertTrue(channel > 245, "expected white, got ${Integer.toHexString(pixel)}")
        }
    }

    @Test
    fun `a picture the wrong shape is refused`() {
        val session = signUp()

        upload(session.token, "avatar", picture(800, 400)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ImageWrongShape") }
        }
        upload(session.token, "banner", picture(800, 800)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ImageWrongShape") }
        }
    }

    @Test
    fun `a picture too small to look sharp is refused`() {
        val session = signUp()

        upload(session.token, "avatar", picture(200, 200)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ImageTooSmall") }
        }
        upload(session.token, "banner", picture(600, 200)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ImageTooSmall") }
        }
    }

    @Test
    fun `something that is not a JPEG or PNG is refused`() {
        val session = signUp()

        upload(session.token, "avatar", "not a picture".toByteArray()).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ImageUnreadable") }
        }
        // A real PNG header with nothing after it: passes the size checks, fails to decode.
        upload(session.token, "avatar", pngHeaderClaiming(600, 600)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ImageUnreadable") }
        }
    }

    @Test
    fun `a small file claiming enormous dimensions is refused before it is decoded`() {
        val session = signUp()

        upload(session.token, "avatar", pngHeaderClaiming(20_000, 20_000)).andExpect {
            status { isEqualTo(413) }
            jsonPath("$.code") { value("ImageTooLarge") }
        }
    }

    @Test
    fun `a file over 5 MB is refused`() {
        val session = signUp()

        upload(session.token, "avatar", Random.nextBytes(6 * 1024 * 1024)).andExpect {
            status { isEqualTo(413) }
            jsonPath("$.code") { value("ImageTooLarge") }
        }
    }

    @Test
    fun `an upload without the picture is a bad request`() {
        val session = signUp()

        mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/avatar") {
            file(MockMultipartFile("photo", "picture.png", MediaType.IMAGE_PNG_VALUE, picture(600, 600)))
            header(HttpHeaders.AUTHORIZATION, "Bearer ${session.token}")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ValidationFailed") }
        }
    }

    @Test
    fun `replacing a picture deletes the old file`() {
        val session = signUp()
        val first = upload(session.token, "avatar", picture(600, 600)).storedUrl("avatarUrl")

        val second = upload(session.token, "avatar", picture(700, 700)).andExpect { status { isOk() } }.storedUrl("avatarUrl")

        assertNotEquals(first, second, "a new address, so no cache can show the old picture")
        assertFalse(Files.exists(fileOf(first)))
        assertTrue(Files.exists(fileOf(second)))
    }

    @Test
    fun `removing a picture clears it and deletes the file`() {
        val session = signUp()
        val url = upload(session.token, "banner", picture(1500, 500)).storedUrl("bannerUrl")

        remove(session.token, "banner").andExpect {
            status { isOk() }
            jsonPath("$.user.bannerUrl") { doesNotExist() }
        }

        assertFalse(Files.exists(fileOf(url)))
    }

    @Test
    fun `a photo kept elsewhere, like a Google one, is replaced without trouble`() {
        val session = signUp()
        users.findByEmailIgnoringCase(session.email).get().avatarUrl = "https://lh3.googleusercontent.com/a/photo"

        val url = upload(session.token, "avatar", picture(600, 600)).andExpect { status { isOk() } }.storedUrl("avatarUrl")

        assertTrue(url.startsWith(BASE_URL))
    }

    @Test
    fun `the profile update can neither set nor clear the pictures`() {
        val session = signUp()
        val avatar = upload(session.token, "avatar", picture(600, 600)).storedUrl("avatarUrl")

        mockMvc.put("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${session.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Amelia Hart","avatarUrl":"https://example.com/x.png","bannerUrl":"https://example.com/y.png"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.avatarUrl") { value(avatar) }
            jsonPath("$.user.bannerUrl") { doesNotExist() }
        }
        // Left out entirely, as the app does: still there.
        mockMvc.put("/api/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${session.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Amelia Hart"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.avatarUrl") { value(avatar) }
        }
    }

    @Test
    fun `deleting the account deletes its pictures`() {
        val session = signUp()
        val avatar = upload(session.token, "avatar", picture(600, 600)).storedUrl("avatarUrl")
        val banner = upload(session.token, "banner", picture(1500, 500)).storedUrl("bannerUrl")

        mockMvc.delete("/api/v1/users/me") { header(HttpHeaders.AUTHORIZATION, "Bearer ${session.token}") }
            .andExpect { status { isNoContent() } }

        assertFalse(Files.exists(fileOf(avatar)))
        assertFalse(Files.exists(fileOf(banner)))
    }

    @Test
    fun `a stored picture is served to anyone who has its address`() {
        val session = signUp()
        val url = upload(session.token, "avatar", picture(600, 600)).storedUrl("avatarUrl")

        val served = mockMvc.get(url.removePrefix(BASE_URL.removeSuffix("/files"))).andExpect {
            status { isOk() }
            content { contentType(MediaType.IMAGE_JPEG) }
        }.andReturn().response.contentAsByteArray

        assertEquals(512, ImageIO.read(ByteArrayInputStream(served)).width)
    }

    @Test
    fun `uploading needs a signed-in account`() {
        upload(null, "avatar", picture(600, 600)).andExpect { status { isUnauthorized() } }
    }
}
