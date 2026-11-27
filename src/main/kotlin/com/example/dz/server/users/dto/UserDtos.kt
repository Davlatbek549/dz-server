package com.example.dz.server.users.dto

import com.example.dz.server.users.entity.Gender
import com.example.dz.server.users.entity.ProfileVisibility
import com.example.dz.server.users.entity.SpecialityKind
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

/** Mirrors the app's profile model so the client DTO maps one to one. */
data class ProfileResponse(
    val user: ProfileUser,
    val bio: String?,
    val phoneNumber: String?,
    val phoneCountry: String?,
    val language: String?,
    val currentGoalMinutes: Int?,
    val dateOfBirth: LocalDate?,
    val gender: Gender?,
    val specialityKind: SpecialityKind?,
    val speciality: String?,
    val address: AddressDto?,
    val profileVisibility: ProfileVisibility,
    val booksRead: Int,
    val friendsCount: Int,
    val collectionsCount: Int,
)

data class ProfileUser(
    val id: String,
    val name: String,
    val email: String?,
    val emailVerified: Boolean,
    val username: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
)

/** Every part is optional; a response carries `null` rather than an empty address. */
data class AddressDto(
    @field:Pattern(regexp = "^[A-Za-z]{2}$", message = "Use a two-letter country code")
    val country: String? = null,

    @field:Size(max = 100, message = "City is too long")
    val city: String? = null,

    @field:Size(max = 200, message = "Street address is too long")
    val street: String? = null,

    @field:Size(max = 20, message = "Postal code is too long")
    val postalCode: String? = null,
)

/**
 * Counts shown on the profile screen. They belong to the library, friends and
 * collections modules. The friends module now exists, but all three are kept
 * together here, so [NONE] is still reported for every count rather than
 * wiring up one and leaving the other two looking inconsistently stale.
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
 * A full replacement of the editable profile: anything omitted is cleared, with four
 * exceptions that an omission must never undo:
 *
 * - [username] and [profileVisibility] keep their current value when absent. Clearing a
 *   username would hand it to the next person to ask, and a missing visibility must not
 *   quietly turn a private profile open.
 * - [dateOfBirth] and [gender] can be set once. Absent keeps what is there; a different
 *   value once one is set is refused with a conflict.
 *
 * Email and password are deliberately absent. Changing an address has to go
 * through a verification flow, and changing a password has to prove the old
 * one — neither belongs in a profile edit.
 *
 * So are the profile photo and banner, which have their own upload endpoints. Their addresses are
 * only ever set by the server; a client that could set them could point a profile at any address
 * on the web. Sending them here does nothing, and leaving them out never clears them.
 */
data class UpdateProfileRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name is too long")
    val name: String,

    /** Compared and stored in lower case. */
    @field:Pattern(regexp = USERNAME_PATTERN, message = USERNAME_RULE)
    val username: String? = null,

    @field:Size(max = 500, message = "Bio is too long")
    val bio: String? = null,

    @field:Size(max = 40, message = "Phone number is too long")
    val phoneNumber: String? = null,

    @field:Pattern(regexp = "^[A-Za-z]{2}$", message = "Use a two-letter country code")
    val phoneCountry: String? = null,

    @field:Size(max = 20, message = "Language tag is too long")
    val language: String? = null,

    @field:Min(value = 0, message = "Goal cannot be negative")
    @field:Max(value = 1440, message = "Goal cannot exceed a day")
    val currentGoalMinutes: Int? = null,

    @field:Past(message = "Date of birth must be in the past")
    val dateOfBirth: LocalDate? = null,

    val gender: Gender? = null,

    val specialityKind: SpecialityKind? = null,

    @field:Size(max = 100, message = "Speciality is too long")
    val speciality: String? = null,

    @field:Valid
    val address: AddressDto? = null,

    val profileVisibility: ProfileVisibility? = null,
)

/**
 * Whether [username] (as normalised: trimmed, lower case) could be saved by the caller.
 * [suggestions] are free alternatives, offered only when it is taken.
 */
data class UsernameAvailabilityResponse(
    val username: String,
    val available: Boolean,
    val reason: UsernameUnavailableReason?,
    val suggestions: List<String>,
)

enum class UsernameUnavailableReason { Taken, Invalid }

/** 3–30 letters, digits, dots and underscores. Upper case is accepted and stored lower. */
const val USERNAME_PATTERN = "^[A-Za-z0-9._]{3,30}$"
const val USERNAME_RULE = "Use 3–30 letters, numbers, . or _"
