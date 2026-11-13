package com.example.dz.server.friends.exception

import org.springframework.http.HttpStatus

/** Why a friend-request write was refused, and the status it should answer with. */
enum class FriendErrorCode(val status: HttpStatus) {
    SelfRequest(HttpStatus.BAD_REQUEST),
    /** `targetUserId` wasn't even a parseable id -- same status as a real id with no user behind it. */
    InvalidTarget(HttpStatus.BAD_REQUEST),
    AlreadyPending(HttpStatus.CONFLICT),
    AlreadyFriends(HttpStatus.CONFLICT),
}

/**
 * A friend request that is invalid given the current state between the two
 * users -- as opposed to [FriendRequestNotFoundException]/[FriendNotFoundException],
 * which are about a missing id. Carries its own message, unlike the
 * `*NotFoundException` family, because the three [FriendErrorCode] cases need
 * to say different things.
 */
class FriendException(val code: FriendErrorCode, override val message: String) : RuntimeException(message)
