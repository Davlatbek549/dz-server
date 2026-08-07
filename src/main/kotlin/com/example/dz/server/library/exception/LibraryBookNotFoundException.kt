package com.example.dz.server.library.exception

/**
 * The caller has no such book. Also what another user's book id looks like from
 * here, which is deliberate: "not yours" and "does not exist" are the same
 * answer, so the API cannot be used to probe other people's libraries.
 */
class LibraryBookNotFoundException(bookId: String) :
    RuntimeException("No library book with id $bookId for this user")
