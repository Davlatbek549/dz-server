package com.example.dz.server.library.service

import com.example.dz.server.library.dto.LibraryBookResponse
import com.example.dz.server.library.dto.LibraryBooksResponse
import com.example.dz.server.library.dto.SaveLibraryBookRequest
import com.example.dz.server.library.dto.UpdateLibraryBookRequest
import com.example.dz.server.library.entity.LibraryBook
import com.example.dz.server.library.exception.LibraryBookNotFoundException
import com.example.dz.server.library.mapper.applySnapshot
import com.example.dz.server.library.mapper.toEntity
import com.example.dz.server.library.mapper.toResponse
import com.example.dz.server.library.repository.LibraryBookRepository
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class LibraryService(private val libraryBooks: LibraryBookRepository) {

    @Transactional(readOnly = true)
    fun listBooks(userId: UUID) = LibraryBooksResponse(
        books = libraryBooks.findAllByUserIdOrderByAddedAtDesc(userId).map { it.toResponse() },
    )

    @Transactional(readOnly = true)
    fun getBook(userId: UUID, bookId: String): LibraryBookResponse =
        requireBook(userId, bookId).toResponse()

    /**
     * Upsert, matching the app's local `INSERT OR REPLACE`: adding a book the
     * user already has refreshes the stored snapshot instead of failing, so a
     * retried sync is harmless.
     */
    fun saveBook(userId: UUID, bookId: String, request: SaveLibraryBookRequest): LibraryBookResponse {
        val existing = libraryBooks.findByUserIdAndBookId(userId, bookId).orElse(null)
        val book = if (existing == null) {
            libraryBooks.save(request.toEntity(userId, bookId))
        } else {
            existing.applySnapshot(request)
            existing
        }
        return book.toResponse()
    }

    /** Partial update for the frequent writes: reading progress and favourite. */
    fun updateBook(userId: UUID, bookId: String, request: UpdateLibraryBookRequest): LibraryBookResponse {
        val book = requireBook(userId, bookId)

        request.progressPercent?.let { percent ->
            book.progressPercent = percent
            // Any progress write is a read, which is what "continue reading" orders by.
            book.lastReadAt = Instant.now()
        }
        request.isFavorite?.let { book.isFavorite = it }

        return book.toResponse()
    }

    fun removeBook(userId: UUID, bookId: String) {
        if (libraryBooks.deleteByUserIdAndBookId(userId, bookId) == 0L) {
            throw LibraryBookNotFoundException(bookId)
        }
    }

    @Transactional(readOnly = true)
    fun continueReading(userId: UUID): LibraryBookResponse? =
        libraryBooks.findContinueReading(userId, PageRequest.of(0, 1))
            .firstOrNull()
            ?.toResponse()

    /**
     * The one lookup this module does, always scoped by the caller. A book id
     * belonging to someone else simply is not found.
     */
    private fun requireBook(userId: UUID, bookId: String): LibraryBook =
        libraryBooks.findByUserIdAndBookId(userId, bookId)
            .orElseThrow { LibraryBookNotFoundException(bookId) }
}
