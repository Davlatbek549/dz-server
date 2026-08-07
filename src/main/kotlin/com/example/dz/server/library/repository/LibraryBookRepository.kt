package com.example.dz.server.library.repository

import com.example.dz.server.library.entity.LibraryBook
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * Every method takes a `userId`. There is no `findByBookId`, so no caller can
 * accidentally reach a row belonging to someone else.
 */
interface LibraryBookRepository : JpaRepository<LibraryBook, UUID> {

    fun findAllByUserIdOrderByAddedAtDesc(userId: UUID): List<LibraryBook>

    fun findByUserIdAndBookId(userId: UUID, bookId: String): Optional<LibraryBook>

    fun deleteByUserIdAndBookId(userId: UUID, bookId: String): Long

    /**
     * Books that are started but unfinished, most recently read first. Falls
     * back to when the book was added for rows that predate any reading.
     */
    @Query(
        """
        select b from LibraryBook b
         where b.userId = :userId
           and b.progressPercent > 0
           and b.progressPercent < 100
         order by coalesce(b.lastReadAt, b.addedAt) desc
        """
    )
    fun findContinueReading(userId: UUID, pageable: Pageable): List<LibraryBook>
}
