package com.example.dz.server.users.exception

import org.springframework.http.HttpStatus

/** Why a profile write was refused, and the status it should answer with. */
enum class ProfileErrorCode(val status: HttpStatus) {
    /** Someone else already has this username (compared case-insensitively). */
    UsernameTaken(HttpStatus.CONFLICT),

    /** The date of birth is already set and can only be changed by support. */
    DateOfBirthLocked(HttpStatus.CONFLICT),

    /** The gender is already set and can only be changed by support. */
    GenderLocked(HttpStatus.CONFLICT),

    /** A date of birth that cannot be real: before 1900. Future dates fail validation instead. */
    InvalidDateOfBirth(HttpStatus.BAD_REQUEST),

    /** The upload is not a JPEG or PNG, or is damaged. */
    ImageUnreadable(HttpStatus.BAD_REQUEST),

    /** A profile photo that is not square, or a banner that is not 3:1. */
    ImageWrongShape(HttpStatus.BAD_REQUEST),

    /** Too few pixels to look sharp at the size it is shown. */
    ImageTooSmall(HttpStatus.BAD_REQUEST),

    /** Over 5 MB, or more pixels than the server will decode. */
    ImageTooLarge(HttpStatus.CONTENT_TOO_LARGE),

    /** This server has nowhere to keep photos (no storage configured). */
    PhotosUnavailable(HttpStatus.SERVICE_UNAVAILABLE),
}

/**
 * A profile edit that is well-formed but not allowed given what is already stored.
 * The app maps [code] onto its own error, so each case can say something different.
 */
class ProfileException(val code: ProfileErrorCode, override val message: String) : RuntimeException(message)
