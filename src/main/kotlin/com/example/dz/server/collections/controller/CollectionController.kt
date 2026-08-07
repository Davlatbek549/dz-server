package com.example.dz.server.collections.controller

import com.example.dz.server.collections.dto.CollectionResponse
import com.example.dz.server.collections.dto.CollectionsResponse
import com.example.dz.server.collections.dto.SaveCollectionRequest
import com.example.dz.server.collections.service.CollectionService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * `{collectionId}` is the id the app generated. It is only ever resolved
 * together with the caller's id from the token, so someone else's collection id
 * resolves to nothing here — and the books inside are reached solely through
 * their parent, which means membership needs no separate ownership check.
 */
@RestController
@RequestMapping("/api/v1/collections")
class CollectionController(private val collectionService: CollectionService) {

    @GetMapping
    fun listCollections(@AuthenticationPrincipal userId: UUID): CollectionsResponse =
        collectionService.listCollections(userId)

    @GetMapping("/{collectionId}")
    fun getCollection(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable collectionId: String,
    ): CollectionResponse = collectionService.getCollection(userId, collectionId)

    @PutMapping("/{collectionId}")
    fun saveCollection(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable collectionId: String,
        @Valid @RequestBody request: SaveCollectionRequest,
    ): CollectionResponse = collectionService.saveCollection(userId, collectionId, request)

    @DeleteMapping("/{collectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCollection(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable collectionId: String,
    ) = collectionService.deleteCollection(userId, collectionId)
}
