package com.example.dz.server.friends.service

import com.example.dz.server.friends.dto.FriendRequestResponse
import com.example.dz.server.friends.dto.FriendRequestsResponse
import com.example.dz.server.friends.dto.FriendResponse
import com.example.dz.server.friends.dto.FriendsResponse
import com.example.dz.server.friends.dto.SuggestedUsersResponse
import com.example.dz.server.friends.entity.FriendRequest
import com.example.dz.server.friends.entity.FriendRequestStatus
import com.example.dz.server.friends.exception.FriendErrorCode
import com.example.dz.server.friends.exception.FriendException
import com.example.dz.server.friends.exception.FriendNotFoundException
import com.example.dz.server.friends.exception.FriendRequestNotFoundException
import com.example.dz.server.friends.mapper.toFriendResponse
import com.example.dz.server.friends.mapper.toFriendSummary
import com.example.dz.server.friends.mapper.toResponse
import com.example.dz.server.friends.repository.FriendRequestRepository
import com.example.dz.server.library.dto.LibraryBookResponse
import com.example.dz.server.library.mapper.toResponse as toLibraryBookResponse
import com.example.dz.server.library.repository.LibraryBookRepository
import com.example.dz.server.notifications.entity.NotificationCategory
import com.example.dz.server.notifications.service.NotificationService
import com.example.dz.server.users.exception.UserNotFoundException
import com.example.dz.server.users.repository.UserRepository
import java.time.Instant
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class FriendService(
    private val friendRequests: FriendRequestRepository,
    private val users: UserRepository,
    private val libraryBooks: LibraryBookRepository,
    private val notificationService: NotificationService,
) {

    @Transactional(readOnly = true)
    fun listFriends(userId: UUID): FriendsResponse {
        val friendIds = friendRequests.findAllForUser(userId, FriendRequestStatus.Accepted)
            .map { other(it, userId) }
        val friends = users.findAllById(friendIds).map { it.toFriendResponse(currentBookOf(it.requireId())) }
        return FriendsResponse(friends = friends)
    }

    @Transactional(readOnly = true)
    fun getFriend(userId: UUID, friendId: UUID): FriendResponse {
        friendRequests.findBetween(userId, friendId, FriendRequestStatus.Accepted)
            .orElseThrow { FriendNotFoundException(friendId.toString()) }
        val friend = users.findById(friendId).orElseThrow { FriendNotFoundException(friendId.toString()) }
        return friend.toFriendResponse(currentBookOf(friendId))
    }

    @Transactional(readOnly = true)
    fun listPendingRequests(userId: UUID): FriendRequestsResponse {
        val requests = friendRequests.findAllByAddresseeIdAndStatusOrderByCreatedAtDesc(userId, FriendRequestStatus.Pending)
        val requesters = users.findAllById(requests.map { it.requesterId }).associateBy { it.requireId() }
        return FriendRequestsResponse(
            requests = requests.map { request ->
                request.toResponse(fromUser = requesters.getValue(request.requesterId).toFriendSummary())
            },
        )
    }

    /**
     * Real dz accounts the caller has no relationship with yet -- not a friend, and no request
     * pending either direction. [other] already knows how to pick "the other side" of a request,
     * so the exclusion set reuses it rather than a dedicated query.
     */
    @Transactional(readOnly = true)
    fun listSuggested(userId: UUID): SuggestedUsersResponse {
        val related = (
            friendRequests.findAllForUser(userId, FriendRequestStatus.Accepted) +
                friendRequests.findAllForUser(userId, FriendRequestStatus.Pending)
            ).map { other(it, userId) }
        val excluded = related.toSet() + userId
        val suggestions = users.findDiscoverable(excluded, PageRequest.of(0, SUGGESTED_PAGE_SIZE))
        return SuggestedUsersResponse(users = suggestions.map { it.toFriendSummary() })
    }

    fun sendRequest(userId: UUID, targetUserIdRaw: String): FriendRequestResponse {
        val targetUserId = try {
            UUID.fromString(targetUserIdRaw)
        } catch (e: IllegalArgumentException) {
            throw FriendException(FriendErrorCode.InvalidTarget, "No such user")
        }
        if (targetUserId == userId) {
            throw FriendException(FriendErrorCode.SelfRequest, "You can't send a friend request to yourself")
        }
        users.findById(targetUserId).orElseThrow { UserNotFoundException(targetUserId) }

        friendRequests.findBetween(userId, targetUserId, FriendRequestStatus.Accepted).ifPresent {
            throw FriendException(FriendErrorCode.AlreadyFriends, "You're already friends with this user")
        }
        friendRequests.findBetween(userId, targetUserId, FriendRequestStatus.Pending).ifPresent {
            throw FriendException(FriendErrorCode.AlreadyPending, "A friend request is already pending between you two")
        }

        val requester = users.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val saved = try {
            friendRequests.save(FriendRequest(requesterId = userId, addresseeId = targetUserId))
        } catch (e: DataIntegrityViolationException) {
            // The partial unique index caught a race the two checks above missed.
            throw FriendException(FriendErrorCode.AlreadyPending, "A friend request is already pending between you two")
        }

        notificationService.notify(
            userId = targetUserId,
            category = NotificationCategory.Friends,
            title = "New friend request",
            message = "${requester.name} wants to be friends",
        )

        return saved.toResponse(fromUser = requester.toFriendSummary())
    }

    fun acceptRequest(userId: UUID, requestId: UUID): FriendResponse {
        val request = friendRequests.findByIdAndAddresseeId(requestId, userId)
            .orElseThrow { FriendRequestNotFoundException(requestId.toString()) }
        request.status = FriendRequestStatus.Accepted
        request.respondedAt = Instant.now()

        val accepter = users.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val requester = users.findById(request.requesterId).orElseThrow { UserNotFoundException(request.requesterId) }

        notificationService.notify(
            userId = request.requesterId,
            category = NotificationCategory.Friends,
            title = "Friend request accepted",
            message = "${accepter.name} accepted your friend request",
        )

        return requester.toFriendResponse(currentBookOf(requester.requireId()))
    }

    fun declineRequest(userId: UUID, requestId: UUID) {
        val request = friendRequests.findByIdAndAddresseeId(requestId, userId)
            .orElseThrow { FriendRequestNotFoundException(requestId.toString()) }
        request.status = FriendRequestStatus.Declined
        request.respondedAt = Instant.now()
    }

    private fun currentBookOf(userId: UUID): LibraryBookResponse? =
        libraryBooks.findContinueReading(userId, PageRequest.of(0, 1)).firstOrNull()?.toLibraryBookResponse()

    private fun other(request: FriendRequest, userId: UUID): UUID =
        if (request.requesterId == userId) request.addresseeId else request.requesterId

    private companion object {
        const val SUGGESTED_PAGE_SIZE = 30
    }
}
