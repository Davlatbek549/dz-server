package com.example.dz.server.library.controller

import com.example.dz.server.library.dto.LibraryBookResponse
import com.example.dz.server.library.dto.LibraryBooksResponse
import com.example.dz.server.library.dto.SaveLibraryBookRequest
import com.example.dz.server.library.dto.UpdateLibraryBookRequest
import com.example.dz.server.library.service.LibraryService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * `{bookId}` in the path is safe because it is never the whole key: every
 * lookup pairs it with the caller's id from the token, so another user's book
 * id resolves to nothing here.
 */
@RestController
@RequestMapping("/api/v1/library/books")
class LibraryController(private val libraryService: LibraryService) {

    @GetMapping
    fun listBooks(@AuthenticationPrincipal userId: UUID): LibraryBooksResponse =
        libraryService.listBooks(userId)

    /** Null when nothing is part-read, so the client can hide the section. */
    @GetMapping("/continue-reading")
    fun continueReading(@AuthenticationPrincipal userId: UUID): ResponseEntity<LibraryBookResponse> =
        libraryService.continueReading(userId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.noContent().build()

    @GetMapping("/{bookId}")
    fun getBook(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable bookId: String,
    ): LibraryBookResponse = libraryService.getBook(userId, bookId)

    @PutMapping("/{bookId}")
    fun saveBook(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable bookId: String,
        @Valid @RequestBody request: SaveLibraryBookRequest,
    ): LibraryBookResponse = libraryService.saveBook(userId, bookId, request)

    @PatchMapping("/{bookId}")
    fun updateBook(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable bookId: String,
        @Valid @RequestBody request: UpdateLibraryBookRequest,
    ): LibraryBookResponse = libraryService.updateBook(userId, bookId, request)

    @DeleteMapping("/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeBook(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable bookId: String,
    ) = libraryService.removeBook(userId, bookId)
}
