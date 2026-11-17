package com.example.dz.server.users.mapper

import com.example.dz.server.users.dto.AddressDto
import com.example.dz.server.users.dto.ProfileCounts
import com.example.dz.server.users.dto.ProfileResponse
import com.example.dz.server.users.dto.ProfileUser
import com.example.dz.server.users.entity.User

fun User.toProfileResponse(counts: ProfileCounts) = ProfileResponse(
    user = ProfileUser(
        id = requireId().toString(),
        name = name,
        email = email,
        emailVerified = emailVerified,
        username = username,
        avatarUrl = avatarUrl,
        bannerUrl = bannerUrl,
    ),
    bio = bio,
    phoneNumber = phoneNumber,
    phoneCountry = phoneCountry,
    language = language,
    currentGoalMinutes = currentGoalMinutes,
    dateOfBirth = dateOfBirth,
    gender = gender,
    specialityKind = specialityKind,
    speciality = speciality,
    address = toAddressDto(),
    profileVisibility = profileVisibility,
    booksRead = counts.booksRead,
    friendsCount = counts.friendsCount,
    collectionsCount = counts.collectionsCount,
)

/** `null` when no part of the address is set, so clients never see an all-null object. */
private fun User.toAddressDto(): AddressDto? =
    if (addressCountry == null && addressCity == null && addressStreet == null && addressPostalCode == null) {
        null
    } else {
        AddressDto(
            country = addressCountry,
            city = addressCity,
            street = addressStreet,
            postalCode = addressPostalCode,
        )
    }
