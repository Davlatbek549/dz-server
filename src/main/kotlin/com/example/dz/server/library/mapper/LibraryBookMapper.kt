package com.example.dz.server.library.mapper

import com.example.dz.server.library.dto.LibraryBookResponse
import com.example.dz.server.library.dto.SaveLibraryBookRequest
import com.example.dz.server.library.entity.LibraryBook
import java.util.UUID

fun LibraryBook.toResponse() = LibraryBookResponse(
    bookId = bookId,
    title = title,
    author = author,
    coverUrl = coverUrl,
    description = description,
    textUrl = textUrl,
    isFree = isFree,
    isFavorite = isFavorite,
    progressPercent = progressPercent,
    addedAt = addedAt,
    lastReadAt = lastReadAt,
)

fun SaveLibraryBookRequest.toEntity(userId: UUID, bookId: String) = LibraryBook(
    userId = userId,
    bookId = bookId,
    title = title.trim(),
    author = author?.trim(),
    coverUrl = coverUrl?.trim(),
    description = description?.trim(),
    textUrl = textUrl?.trim(),
    isFree = isFree,
    isFavorite = isFavorite,
    progressPercent = progressPercent,
)

/** Applies a replacement snapshot to an existing row, keeping identity and [LibraryBook.addedAt]. */
fun LibraryBook.applySnapshot(request: SaveLibraryBookRequest) {
    title = request.title.trim()
    author = request.author?.trim()
    coverUrl = request.coverUrl?.trim()
    description = request.description?.trim()
    textUrl = request.textUrl?.trim()
    isFree = request.isFree
    isFavorite = request.isFavorite
    progressPercent = request.progressPercent
}
