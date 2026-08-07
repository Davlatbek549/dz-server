package com.example.dz.server.collections.repository

import com.example.dz.server.collections.entity.BookCollection
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface BookCollectionRepository : JpaRepository<BookCollection, UUID> {

    /**
     * The entity graph loads every collection's books in one query. Without it
     * this is the textbook N+1: one query for the collections, then another per
     * collection when the mapper touches its books.
     */
    @EntityGraph(attributePaths = ["books"])
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<BookCollection>

    @EntityGraph(attributePaths = ["books"])
    fun findByUserIdAndExternalId(userId: UUID, externalId: String): Optional<BookCollection>

    fun deleteByUserIdAndExternalId(userId: UUID, externalId: String): Long
}
