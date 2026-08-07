package com.example.dz.server.users.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Mirrors the app's `UserProfile` so the client DTO maps one to one. */
data class ProfileResponse(
    val user: ProfileUser,
    val bio: String?,
    val phoneNumber: String?,
    val language: String?,
    val currentGoalMinutes: Int?,
    val booksRead: Int,
    val friendsCount: Int,
    val collectionsCount: Int,
)

data class ProfileUser(
    val id: String,
    val name: String,
    val email: String?,
    val avatarUrl: String?,
)

/**
 * Counts shown on the profile screen. They belong to the library, friends and
 * collections modules, so until those exist [NONE] is reported rather than a
 * stored number that could go stale.
 */
data class ProfileCounts(
    val booksRead: Int,
    val friendsCount: Int,
    val collectionsCount: Int,
) {
    companion object {
        val NONE = ProfileCounts(booksRead = 0, friendsCount = 0, collectionsCount = 0)
    }
}

/**
 * A full replacement of the editable profile: anything omitted is cleared.
 *
 * Email and password are deliberately absent. Changing an address has to go
 * through a verification flow, and changing a password has to prove the old
 * one — neither belongs in a profile edit.
 */
data class UpdateProfileRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name is too long")
    val name: String,

    @field:Size(max = 2048, message = "Avatar URL is too long")
    val avatarUrl: String? = null,

    @field:Size(max = 500, message = "Bio is too long")
    val bio: String? = null,

    @field:Size(max = 40, message = "Phone number is too long")
    val phoneNumber: String? = null,

    @field:Size(max = 20, message = "Language tag is too long")
    val language: String? = null,

    @field:Min(value = 0, message = "Goal cannot be negative")
    @field:Max(value = 1440, message = "Goal cannot exceed a day")
    val currentGoalMinutes: Int? = null,
)
