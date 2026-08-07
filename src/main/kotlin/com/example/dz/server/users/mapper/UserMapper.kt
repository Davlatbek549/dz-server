package com.example.dz.server.users.mapper

import com.example.dz.server.users.dto.ProfileCounts
import com.example.dz.server.users.dto.ProfileResponse
import com.example.dz.server.users.dto.ProfileUser
import com.example.dz.server.users.entity.User

fun User.toProfileResponse(counts: ProfileCounts) = ProfileResponse(
    user = ProfileUser(
        id = requireId().toString(),
        name = name,
        email = email,
        avatarUrl = avatarUrl,
    ),
    bio = bio,
    phoneNumber = phoneNumber,
    language = language,
    currentGoalMinutes = currentGoalMinutes,
    booksRead = counts.booksRead,
    friendsCount = counts.friendsCount,
    collectionsCount = counts.collectionsCount,
)
