package com.example.dz.server.users.controller

import com.example.dz.server.users.dto.ProfileResponse
import com.example.dz.server.users.dto.UpdateProfileRequest
import com.example.dz.server.users.service.UserService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * `/me` rather than `/users/{id}`: the caller is identified by their token, so
 * there is no id in the path to tamper with. A `/users/{id}` route would have to
 * compare the path against the token on every call, and the one place that check
 * was forgotten would expose every profile.
 *
 * Reading *other* people's profiles is a separate question — what is public
 * versus private — and belongs with the social module.
 */
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @GetMapping("/me")
    fun profile(@AuthenticationPrincipal userId: UUID): ProfileResponse =
        userService.getProfile(userId)

    @PutMapping("/me")
    fun updateProfile(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ProfileResponse = userService.updateProfile(userId, request)

    /**
     * Deletes the caller's account and everything it owns, for good.
     *
     * The app stores are what require it — an app that lets people make an account must let them
     * delete it from inside the app — but it is also simply theirs to remove. No password is asked
     * for: an account made through Google has never had one it knows, and the app confirms first.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(@AuthenticationPrincipal userId: UUID) {
        userService.deleteAccount(userId)
    }
}
