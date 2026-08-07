package com.example.dz.server.collections.service

import com.example.dz.server.collections.dto.CollectionResponse
import com.example.dz.server.collections.dto.CollectionsResponse
import com.example.dz.server.collections.dto.SaveCollectionRequest
import com.example.dz.server.collections.entity.BookCollection
import com.example.dz.server.collections.exception.CollectionNotFoundException
import com.example.dz.server.collections.mapper.addBooksFrom
import com.example.dz.server.collections.mapper.toResponse
import com.example.dz.server.collections.repository.BookCollectionRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CollectionService(private val collections: BookCollectionRepository) {

    @Transactional(readOnly = true)
    fun listCollections(userId: UUID) = CollectionsResponse(
        collections = collections.findAllByUserIdOrderByCreatedAtDesc(userId).map { it.toResponse() },
    )

    @Transactional(readOnly = true)
    fun getCollection(userId: UUID, collectionId: String): CollectionResponse =
        requireCollection(userId, collectionId).toResponse()

    /**
     * Create or replace under the id the client chose. Upserting keeps a
     * retried sync harmless and matches the app, which has no separate notion
     * of "create" once it has generated the id.
     */
    fun saveCollection(
        userId: UUID,
        collectionId: String,
        request: SaveCollectionRequest,
    ): CollectionResponse {
        val collection = collections.findByUserIdAndExternalId(userId, collectionId)
            .orElseGet {
                collections.save(
                    BookCollection(userId = userId, externalId = collectionId, title = request.title.trim())
                )
            }

        collection.title = request.title.trim()
        collection.description = request.description?.trim()?.ifEmpty { null }

        collection.books.clear()
        // Hibernate orders inserts before deletes within a flush, so re-adding a
        // book that is already there would trip the (collection_id, book_id)
        // unique constraint. Flushing here runs the deletes first.
        collections.flush()
        collection.addBooksFrom(request.books)

        return collection.toResponse()
    }

    fun deleteCollection(userId: UUID, collectionId: String) {
        if (collections.deleteByUserIdAndExternalId(userId, collectionId) == 0L) {
            throw CollectionNotFoundException(collectionId)
        }
    }

    /** The only lookup in this module, and it is always scoped to the caller. */
    private fun requireCollection(userId: UUID, collectionId: String): BookCollection =
        collections.findByUserIdAndExternalId(userId, collectionId)
            .orElseThrow { CollectionNotFoundException(collectionId) }
}
