package com.example.dz.server.users.service

import com.example.dz.server.common.storage.ImageStorage
import com.example.dz.server.users.dto.ProfileCounts
import com.example.dz.server.users.dto.ProfileResponse
import com.example.dz.server.users.entity.User
import com.example.dz.server.users.exception.ProfileErrorCode
import com.example.dz.server.users.exception.ProfileException
import com.example.dz.server.users.exception.UserNotFoundException
import com.example.dz.server.users.mapper.toProfileResponse
import com.example.dz.server.users.repository.UserRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Sets and removes the caller's profile photo and banner.
 *
 * Only the database write runs in a transaction. The picture is checked and resized first, so no
 * connection is held while it decodes, and stored before the row points at it, so the profile
 * never names a file that is not there yet. The file it replaces is deleted only after the new
 * address is saved; if saving fails, the new file is deleted instead and nothing has changed.
 */
@Service
class ProfilePhotoService(
    private val users: UserRepository,
    private val storage: ImageStorage,
    private val processor: ProfileImageProcessor,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    fun upload(userId: UUID, kind: ProfileImageKind, bytes: ByteArray): ProfileResponse {
        if (!storage.acceptsUploads) {
            throw ProfileException(ProfileErrorCode.PhotosUnavailable, "Photos can't be added right now")
        }
        val jpeg = processor.process(kind, bytes)
        val url = storage.put("${kind.folder}/${UUID.randomUUID()}.jpg", jpeg, "image/jpeg")
        val change = try {
            transaction.execute { replace(userId, kind, url) }!!
        } catch (failure: RuntimeException) {
            storage.deleteQuietly(url)
            throw failure
        }
        change.previous?.let(storage::deleteQuietly)
        return change.profile
    }

    fun remove(userId: UUID, kind: ProfileImageKind): ProfileResponse {
        val change = transaction.execute { replace(userId, kind, null) }!!
        change.previous?.let(storage::deleteQuietly)
        return change.profile
    }

    private fun replace(userId: UUID, kind: ProfileImageKind, url: String?): Change {
        val user = users.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val previous = when (kind) {
            ProfileImageKind.Avatar -> user.avatarUrl.also { user.avatarUrl = url }
            ProfileImageKind.Banner -> user.bannerUrl.also { user.bannerUrl = url }
        }
        users.saveAndFlush(user)
        return Change(previous = previous?.takeIf { it != url }, profile = user.profile())
    }

    private fun User.profile(): ProfileResponse = toProfileResponse(ProfileCounts.NONE)

    private class Change(val previous: String?, val profile: ProfileResponse)
}
