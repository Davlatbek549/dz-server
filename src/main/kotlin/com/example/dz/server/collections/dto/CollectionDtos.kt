package com.example.dz.server.collections.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CollectionsResponse(
    val collections: List<CollectionResponse>,
)

/** Mirrors the app's `Collection`: metadata plus its books, in order. */
data class CollectionResponse(
    val id: String,
    val title: String,
    val description: String?,
    val books: List<CollectionBookResponse>,
    val createdAt: Instant,
)

data class CollectionBookResponse(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
)

/**
 * Creates the collection or replaces it wholesale, including its membership —
 * the same shape as the app's local `update`, which deletes every row and
 * re-inserts the list it was given. Sending an empty [books] empties it.
 */
data class SaveCollectionRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 200, message = "Title is too long")
    val title: String,

    @field:Size(max = 1000, message = "Description is too long")
    val description: String? = null,

    @field:Valid
    val books: List<CollectionBookRequest> = emptyList(),
)

data class CollectionBookRequest(
    @field:NotBlank(message = "Book id is required")
    @field:Size(max = 200, message = "Book id is too long")
    val bookId: String,

    @field:NotBlank(message = "Book title is required")
    @field:Size(max = 500, message = "Book title is too long")
    val title: String,

    @field:Size(max = 500, message = "Author is too long")
    val author: String? = null,

    @field:Size(max = 2048, message = "Cover URL is too long")
    val coverUrl: String? = null,
)
