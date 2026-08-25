package com.example.dz.server.auth.mapper

import com.example.dz.server.auth.dto.UserResponse
import com.example.dz.server.users.entity.User

/**
 * The user summary embedded in a session response. The fuller profile
 * representation belongs to the users module, which is why this stays small and
 * auth-owned rather than shared.
 */
fun User.toUserResponse() = UserResponse(
    id = requireId().toString(),
    name = name,
    email = email,
    avatarUrl = avatarUrl,
    emailVerified = emailVerified,
)
