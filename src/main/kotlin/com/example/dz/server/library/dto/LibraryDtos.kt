package com.example.dz.server.library.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Wrapped rather than a bare JSON array so pagination can be added later
 * without changing the response's shape.
 */
data class LibraryBooksResponse(
    val books: List<LibraryBookResponse>,
)

/**
 * Mirrors the app's `LibraryBook`, minus `isDownloaded` and `downloadPath`:
 * those describe one device and stay in the app's local database.
 */
data class LibraryBookResponse(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val description: String?,
    val textUrl: String?,
    val isFree: Boolean,
    val isFavorite: Boolean,
    val progressPercent: Int,
    val addedAt: Instant,
    val lastReadAt: Instant?,
)

/** Adds the book, or replaces the stored snapshot if it is already there. */
data class SaveLibraryBookRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 500, message = "Title is too long")
    val title: String,

    @field:Size(max = 500, message = "Author is too long")
    val author: String? = null,

    @field:Size(max = 2048, message = "Cover URL is too long")
    val coverUrl: String? = null,

    @field:Size(max = 5000, message = "Description is too long")
    val description: String? = null,

    @field:Size(max = 2048, message = "Text URL is too long")
    val textUrl: String? = null,

    val isFree: Boolean = true,
    val isFavorite: Boolean = false,

    @field:Min(value = 0, message = "Progress cannot be negative")
    @field:Max(value = 100, message = "Progress cannot exceed 100")
    val progressPercent: Int = 0,
)

/**
 * The frequent, small write: turning a page or tapping the heart. Omitted
 * fields are left alone, so the client sends only what changed.
 */
data class UpdateLibraryBookRequest(
    @field:Min(value = 0, message = "Progress cannot be negative")
    @field:Max(value = 100, message = "Progress cannot exceed 100")
    val progressPercent: Int? = null,

    val isFavorite: Boolean? = null,
)
