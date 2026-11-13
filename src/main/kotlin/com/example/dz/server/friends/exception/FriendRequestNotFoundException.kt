package com.example.dz.server.friends.exception

/**
 * No such pending request addressed to the caller -- also what another
 * user's request id, or an already-resolved one, looks like from here.
 */
class FriendRequestNotFoundException(id: String) :
    RuntimeException("No pending friend request with id $id for this user")
