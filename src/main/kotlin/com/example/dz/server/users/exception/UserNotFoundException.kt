package com.example.dz.server.users.exception

import java.util.UUID

/**
 * The token was valid but its subject no longer exists — a deleted account
 * whose access token has not expired yet.
 */
class UserNotFoundException(userId: UUID) : RuntimeException("No user with id $userId")
