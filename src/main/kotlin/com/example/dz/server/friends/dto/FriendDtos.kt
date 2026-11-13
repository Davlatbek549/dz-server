package com.example.dz.server.friends.dto

import com.example.dz.server.library.dto.LibraryBookResponse
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Wrapped rather than a bare JSON array so pagination can be added later
 * without changing the response's shape.
 */
data class FriendsResponse(
    val friends: List<FriendResponse>,
)

data class FriendResponse(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val isOnline: Boolean,
    val currentBook: LibraryBookResponse?,
)

data class FriendRequestsResponse(
    val requests: List<FriendRequestResponse>,
)

/** Real dz accounts the caller doesn't already have a relationship with -- see `FriendService.listSuggested`. */
data class SuggestedUsersResponse(
    val users: List<FriendSummary>,
)

data class FriendRequestResponse(
    val id: String,
    val fromUser: FriendSummary,
    val createdAt: Instant,
)

data class FriendSummary(
    val id: String,
    val name: String,
    val avatarUrl: String?,
)

data class SendFriendRequestRequest(
    @field:NotBlank(message = "Target user id is required")
    val targetUserId: String,
)
