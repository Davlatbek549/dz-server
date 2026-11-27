package com.example.dz.server.friends.exception

/**
 * Caller and the given id are not accepted friends -- also what a user who
 * exists but isn't a friend, or doesn't exist at all, looks like from here.
 */
class FriendNotFoundException(id: String) :
    RuntimeException("Not friends with user $id")
