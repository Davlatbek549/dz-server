package com.example.dz.server.users.controller

import com.example.dz.server.users.dto.ProfileResponse
import com.example.dz.server.users.service.ProfileImageKind
import com.example.dz.server.users.service.ProfilePhotoService
import java.util.UUID
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * The caller's profile photo and banner. Uploads are `multipart/form-data` with the picture in a
 * part named `image`: a JPEG or PNG of up to 5 MB, square for the photo and 3:1 for the banner.
 * Every call answers with the whole profile, the same as `GET /users/me`, so the app can show the
 * stored picture's address straight away.
 */
@RestController
@RequestMapping("/api/v1/users/me")
class ProfilePhotoController(private val photos: ProfilePhotoService) {

    @PutMapping("/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAvatar(
        @AuthenticationPrincipal userId: UUID,
        @RequestPart("image") image: MultipartFile,
    ): ProfileResponse = photos.upload(userId, ProfileImageKind.Avatar, image.bytes)

    @DeleteMapping("/avatar")
    fun removeAvatar(@AuthenticationPrincipal userId: UUID): ProfileResponse =
        photos.remove(userId, ProfileImageKind.Avatar)

    @PutMapping("/banner", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadBanner(
        @AuthenticationPrincipal userId: UUID,
        @RequestPart("image") image: MultipartFile,
    ): ProfileResponse = photos.upload(userId, ProfileImageKind.Banner, image.bytes)

    @DeleteMapping("/banner")
    fun removeBanner(@AuthenticationPrincipal userId: UUID): ProfileResponse =
        photos.remove(userId, ProfileImageKind.Banner)
}
