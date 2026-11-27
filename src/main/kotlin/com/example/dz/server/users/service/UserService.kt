package com.example.dz.server.users.service

import com.example.dz.server.common.storage.ImageStorage
import com.example.dz.server.users.dto.ProfileCounts
import com.example.dz.server.users.dto.ProfileResponse
import com.example.dz.server.users.dto.USERNAME_PATTERN
import com.example.dz.server.users.dto.UpdateProfileRequest
import com.example.dz.server.users.dto.UsernameAvailabilityResponse
import com.example.dz.server.users.dto.UsernameUnavailableReason
import com.example.dz.server.users.entity.Gender
import com.example.dz.server.users.entity.User
import com.example.dz.server.users.exception.ProfileErrorCode
import com.example.dz.server.users.exception.ProfileException
import com.example.dz.server.users.exception.UserNotFoundException
import com.example.dz.server.users.mapper.toProfileResponse
import com.example.dz.server.users.repository.UserRepository
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Every method takes the caller's id from the authenticated token rather than
 * from the request, so there is no parameter an attacker could point at someone
 * else's row. This is the pattern the later user-scoped modules follow.
 */
@Service
@Transactional
class UserService(
    private val users: UserRepository,
    private val imageStorage: ImageStorage,
) {

    @Transactional(readOnly = true)
    fun getProfile(userId: UUID): ProfileResponse =
        requireUser(userId).toProfileResponse(ProfileCounts.NONE)

    /** See [UpdateProfileRequest] for which fields an omission clears and which it keeps. */
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): ProfileResponse {
        val user = requireUser(userId)

        user.name = request.name.trim()
        user.bio = request.bio?.trimOrNull()
        user.phoneNumber = request.phoneNumber?.trimOrNull()
        user.phoneCountry = request.phoneCountry?.trimOrNull()?.uppercase(Locale.ROOT)
        user.language = request.language?.trimOrNull()
        user.currentGoalMinutes = request.currentGoalMinutes
        user.specialityKind = request.specialityKind
        user.speciality = request.speciality?.trimOrNull()

        val address = request.address
        user.addressCountry = address?.country?.trimOrNull()?.uppercase(Locale.ROOT)
        user.addressCity = address?.city?.trimOrNull()
        user.addressStreet = address?.street?.trimOrNull()
        user.addressPostalCode = address?.postalCode?.trimOrNull()

        request.profileVisibility?.let { user.profileVisibility = it }
        request.dateOfBirth?.let { applyDateOfBirth(user, it) }
        request.gender?.let { applyGender(user, it) }
        request.username?.let { applyUsername(user, it) }

        // Flushed here rather than on commit so a username claimed by someone else between the
        // check above and this write fails as a conflict the app understands, not a 500.
        try {
            users.saveAndFlush(user)
        } catch (race: DataIntegrityViolationException) {
            throw ProfileException(ProfileErrorCode.UsernameTaken, "That username is taken")
        }
        return user.toProfileResponse(ProfileCounts.NONE)
    }

    /**
     * Whether [rawUsername] could be saved by [userId] right now. Your own current username is
     * available to you. Suggestions are only worked out when it is taken, and are themselves free
     * at the moment of asking — not reserved.
     */
    @Transactional(readOnly = true)
    fun checkUsername(userId: UUID, rawUsername: String): UsernameAvailabilityResponse {
        val username = rawUsername.normaliseUsername()
        if (!USERNAME_REGEX.matches(username)) {
            return UsernameAvailabilityResponse(username, false, UsernameUnavailableReason.Invalid, emptyList())
        }
        if (!users.isUsernameTakenByOther(username, userId)) {
            return UsernameAvailabilityResponse(username, true, null, emptyList())
        }
        val user = requireUser(userId)
        return UsernameAvailabilityResponse(
            username = username,
            available = false,
            reason = UsernameUnavailableReason.Taken,
            suggestions = suggestionsFor(username, user),
        )
    }

    private fun applyUsername(user: User, raw: String) {
        val username = raw.normaliseUsername()
        if (username == user.username) return
        if (users.isUsernameTakenByOther(username, user.requireId())) {
            throw ProfileException(ProfileErrorCode.UsernameTaken, "That username is taken")
        }
        user.username = username
    }

    private fun applyDateOfBirth(user: User, requested: LocalDate) {
        val current = user.dateOfBirth
        when {
            current == null -> {
                if (requested.isBefore(EARLIEST_DATE_OF_BIRTH)) {
                    throw ProfileException(ProfileErrorCode.InvalidDateOfBirth, "Check the year of your date of birth")
                }
                user.dateOfBirth = requested
            }
            current != requested ->
                throw ProfileException(ProfileErrorCode.DateOfBirthLocked, "Date of birth can only be set once")
        }
    }

    private fun applyGender(user: User, requested: Gender) {
        val current = user.gender
        when {
            current == null -> user.gender = requested
            current != requested ->
                throw ProfileException(ProfileErrorCode.GenderLocked, "Gender can only be set once")
        }
    }

    /**
     * Up to two free alternatives, most personal first: `name.surname`-style from the account's
     * name, then a reading-flavoured one, then numbered. Built only from characters a username
     * may hold, so every one passes validation as offered.
     */
    private fun suggestionsFor(taken: String, user: User): List<String> {
        val base = taken.filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
            .trim('.', '_')
            .take(MAX_SUGGESTION_BASE)
            .ifEmpty { "reader" }
        val nameParts = user.name.lowercase(Locale.ROOT)
            .split(WHITESPACE)
            .map { part -> part.filter { it in 'a'..'z' || it in '0'..'9' } }
            .filter { it.isNotEmpty() }
        val surname = nameParts.drop(1).lastOrNull()

        val candidates = buildList {
            surname?.let { add("$base.$it") }
            add("${base}_reads")
            if (surname != null) add("${nameParts.first()}.$surname")
            (2..30).forEach { add("$base$it") }
        }
        return candidates.asSequence()
            .distinct()
            .filter { it != taken && USERNAME_REGEX.matches(it) }
            .filter { !users.isUsernameTakenByOther(it, user.requireId()) }
            .take(2)
            .toList()
    }

    /**
     * Removes the account and, through the schema's `on delete cascade`, everything that points at
     * it: sessions, library, collections and their books, linked Google identities, outstanding
     * codes. There is nothing to delete by hand because every owning table already says so.
     *
     * The profile photo and banner live outside the database, so those files are deleted here once
     * the rows are gone. Flushed first: if the database refuses the delete, the photos stay with
     * the account that still shows them.
     *
     * Idempotent: an account that is already gone is a success. What the caller asked for — no
     * account — already holds, and a retry after a lost response must not come back as an error.
     * That relies on [com.example.dz.server.auth.jwt.EmailVerifiedFilter] letting this request
     * through for an account it can no longer find.
     */
    fun deleteAccount(userId: UUID) {
        val photos = users.findById(userId).map { listOfNotNull(it.avatarUrl, it.bannerUrl) }.orElse(emptyList())
        users.deleteById(userId)
        users.flush()
        photos.forEach(imageStorage::deleteQuietly)
    }

    private fun requireUser(userId: UUID): User =
        users.findById(userId).orElseThrow { UserNotFoundException(userId) }

    /** Treats a whitespace-only value as absent, so blanks never reach the column. */
    private fun String.trimOrNull(): String? = trim().ifEmpty { null }

    /** Accepts "@Amelia.Reads " as typed and stores "amelia.reads". */
    private fun String.normaliseUsername(): String = trim().removePrefix("@").lowercase(Locale.ROOT)

    private companion object {
        val USERNAME_REGEX = Regex(USERNAME_PATTERN)
        val WHITESPACE = Regex("\\s+")
        val EARLIEST_DATE_OF_BIRTH: LocalDate = LocalDate.of(1900, 1, 1)

        /** Leaves room for a suffix like "_reads" or ".surname" inside the 30-character limit. */
        const val MAX_SUGGESTION_BASE = 20
    }
}
