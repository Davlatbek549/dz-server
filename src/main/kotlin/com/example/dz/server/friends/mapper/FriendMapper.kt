package com.example.dz.server.friends.mapper

import com.example.dz.server.friends.dto.FriendRequestResponse
import com.example.dz.server.friends.dto.FriendResponse
import com.example.dz.server.friends.dto.FriendSummary
import com.example.dz.server.friends.entity.FriendRequest
import com.example.dz.server.library.dto.LibraryBookResponse
import com.example.dz.server.users.entity.User

fun User.toFriendSummary() = FriendSummary(
    id = requireId().toString(),
    name = name,
    avatarUrl = avatarUrl,
)

/** No presence tracking exists yet, so [isOnline] always reports `false`. */
fun User.toFriendResponse(currentBook: LibraryBookResponse?, isOnline: Boolean = false) = FriendResponse(
    id = requireId().toString(),
    name = name,
    avatarUrl = avatarUrl,
    isOnline = isOnline,
    currentBook = currentBook,
)

fun FriendRequest.toResponse(fromUser: FriendSummary) = FriendRequestResponse(
    id = requireId().toString(),
    fromUser = fromUser,
    createdAt = createdAt,
)
