package com.example.dz.server.collections.mapper

import com.example.dz.server.collections.dto.CollectionBookRequest
import com.example.dz.server.collections.dto.CollectionBookResponse
import com.example.dz.server.collections.dto.CollectionResponse
import com.example.dz.server.collections.entity.BookCollection
import com.example.dz.server.collections.entity.CollectionBook

fun BookCollection.toResponse() = CollectionResponse(
    id = externalId,
    title = title,
    description = description,
    books = books.map { it.toResponse() },
    createdAt = createdAt,
)

fun CollectionBook.toResponse() = CollectionBookResponse(
    bookId = bookId,
    title = title,
    author = author,
    coverUrl = coverUrl,
)

/**
 * Fills the collection's membership from a request, keeping the order sent.
 *
 * Duplicate book ids are collapsed rather than rejected: a client that sends
 * the same book twice means it once, and the unique constraint would otherwise
 * turn a harmless mistake into a 500.
 */
fun BookCollection.addBooksFrom(requests: List<CollectionBookRequest>) {
    requests
        .distinctBy { it.bookId.trim() }
        .forEachIndexed { index, request ->
            books.add(
                CollectionBook(
                    collection = this,
                    bookId = request.bookId.trim(),
                    title = request.title.trim(),
                    author = request.author?.trim(),
                    coverUrl = request.coverUrl?.trim(),
                    position = index,
                )
            )
        }
}
