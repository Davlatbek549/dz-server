package com.example.dz.server.users.service

import com.example.dz.server.users.dto.ProfileCounts
import com.example.dz.server.users.dto.ProfileResponse
import com.example.dz.server.users.dto.UpdateProfileRequest
import com.example.dz.server.users.entity.User
import com.example.dz.server.users.exception.UserNotFoundException
import com.example.dz.server.users.mapper.toProfileResponse
import com.example.dz.server.users.repository.UserRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Every method takes the caller's id from the authenticated token rather than
 * from the request, so there is no parameter an attacker could point at someone
 * else's row. This is the pattern the later user-scoped modules follow.
 */
@Service
@Transactional
class UserService(private val users: UserRepository) {

    @Transactional(readOnly = true)
    fun getProfile(userId: UUID): ProfileResponse =
        requireUser(userId).toProfileResponse(ProfileCounts.NONE)

    fun updateProfile(userId: UUID, request: UpdateProfileRequest): ProfileResponse {
        val user = requireUser(userId)

        user.name = request.name.trim()
        user.avatarUrl = request.avatarUrl?.trimOrNull()
        user.bio = request.bio?.trimOrNull()
        user.phoneNumber = request.phoneNumber?.trimOrNull()
        user.language = request.language?.trimOrNull()
        user.currentGoalMinutes = request.currentGoalMinutes

        // Managed entity: the transaction flushes the changes on commit.
        return user.toProfileResponse(ProfileCounts.NONE)
    }

    private fun requireUser(userId: UUID): User =
        users.findById(userId).orElseThrow { UserNotFoundException(userId) }

    /** Treats a whitespace-only value as absent, so blanks never reach the column. */
    private fun String.trimOrNull(): String? = trim().ifEmpty { null }
}
